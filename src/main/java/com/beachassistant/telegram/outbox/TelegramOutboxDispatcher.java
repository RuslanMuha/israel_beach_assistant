package com.beachassistant.telegram.outbox;

import com.beachassistant.telegram.client.TelegramBotSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Polls {@code telegram_outbox} and delivers messages to Telegram. Handles 429 via Retry-After,
 * exponential backoff for other errors, and terminal FAILED state after max attempts.
 * Single-flight per JVM; safe on single-instance deployments.
 */
@Slf4j
@Component
@Profile("!test")
public class TelegramOutboxDispatcher {

    private final TelegramOutboxRepository repository;
    private final TelegramOutboxProperties properties;
    private final TelegramBotSender botSender;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final TransactionTemplate txTemplate;

    public TelegramOutboxDispatcher(TelegramOutboxRepository repository,
                                    TelegramOutboxProperties properties,
                                    TelegramBotSender botSender,
                                    ObjectMapper objectMapper,
                                    Clock clock,
                                    TransactionTemplate txTemplate) {
        this.repository = repository;
        this.properties = properties;
        this.botSender = botSender;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.txTemplate = txTemplate;
    }

    @Scheduled(fixedDelayString = "${beach.telegram.outbox.poll-interval:500ms}")
    public void tick() {
        if (!properties.isEnabled()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        List<Long> ids = repository.findPendingIds(now, PageRequest.of(0, properties.getBatchSize()));
        for (Long id : ids) {
            try {
                deliver(id);
            } catch (Exception e) {
                log.warn("Outbox delivery loop error for id={}: {}", id, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 300_000L)
    public void purgeCompleted() {
        txTemplate.executeWithoutResult(status -> {
            ZonedDateTime sentCutoff = ZonedDateTime.now(clock).minus(properties.getRetentionAfterSent());
            ZonedDateTime failedCutoff = ZonedDateTime.now(clock).minus(properties.getRetentionFailed());
            repository.findAll().stream()
                    .filter(e -> (e.getStatus() == OutboxStatus.SENT && e.getSentAt() != null && e.getSentAt().isBefore(sentCutoff))
                            || (e.getStatus() == OutboxStatus.FAILED && e.getCreatedAt().isBefore(failedCutoff)))
                    .forEach(repository::delete);
        });
    }

    private void deliver(Long id) {
        TelegramOutboxEntity entity = txTemplate.execute(status -> {
            int claimed = repository.claim(id);
            if (claimed == 0) {
                return null;
            }
            return repository.findById(id).orElse(null);
        });
        if (entity == null) {
            return;
        }
        try {
            send(entity);
            txTemplate.executeWithoutResult(status -> {
                entity.setStatus(OutboxStatus.SENT);
                entity.setSentAt(ZonedDateTime.now(clock));
                entity.setLastError(null);
                repository.save(entity);
            });
        } catch (TelegramApiRequestException e) {
            txTemplate.executeWithoutResult(s -> handleFailure(entity, e, retryAfterOf(e)));
        } catch (Exception e) {
            txTemplate.executeWithoutResult(s -> handleFailure(entity, e, null));
        }
    }

    private void send(TelegramOutboxEntity entity) throws TelegramApiException, com.fasterxml.jackson.core.JsonProcessingException {
        OutboxPayload payload = objectMapper.readValue(entity.getPayloadJson(), OutboxPayload.class);
        long chatId = entity.getChatId();
        InlineKeyboardMarkup keyboard = payload.replyMarkup() == null
                ? null
                : objectMapper.treeToValue(payload.replyMarkup(), InlineKeyboardMarkup.class);
        switch (entity.getMessageType()) {
            case TEXT -> botSender.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(payload.text())
                    .parseMode(payload.parseMode())
                    .build());
            case TEXT_WITH_KEYBOARD -> botSender.execute(SendMessage.builder()
                    .chatId(chatId)
                    .text(payload.text())
                    .parseMode(payload.parseMode())
                    .replyMarkup(keyboard)
                    .build());
            case PHOTO -> {
                SendPhoto.SendPhotoBuilder b = SendPhoto.builder()
                        .chatId(chatId)
                        .photo(new InputFile(payload.photoUrl()));
                if (payload.caption() != null) {
                    b.caption(payload.caption());
                }
                if (keyboard != null) {
                    b.replyMarkup(keyboard);
                }
                botSender.execute(b.build());
            }
            case CHAT_ACTION -> log.debug("CHAT_ACTION bypasses outbox; dropping stale entry id={}", entity.getId());
        }
    }

    private void handleFailure(TelegramOutboxEntity entity, Throwable cause, Duration retryAfterHint) {
        entity.setLastError(truncate(cause.getMessage(), 1000));
        if (entity.getAttempts() >= entity.getMaxAttempts()) {
            entity.setStatus(OutboxStatus.FAILED);
            log.error("Outbox message id={} chat={} permanently failed after {} attempts: {}",
                    entity.getId(), entity.getChatId(), entity.getAttempts(), cause.getMessage());
        } else {
            entity.setStatus(OutboxStatus.PENDING);
            Duration backoff = retryAfterHint != null ? retryAfterHint : computeBackoff(entity.getAttempts());
            entity.setNextAttemptAt(ZonedDateTime.now(clock).plus(backoff));
            log.warn("Outbox message id={} chat={} attempt {} failed, retry in {}s: {}",
                    entity.getId(), entity.getChatId(), entity.getAttempts(), backoff.toSeconds(), cause.getMessage());
        }
        repository.save(entity);
    }

    private Duration computeBackoff(int attempts) {
        long millis = (long) (properties.getInitialBackoff().toMillis() * Math.pow(2, Math.max(0, attempts - 1)));
        long capped = Math.min(millis, properties.getMaxBackoff().toMillis());
        return Duration.ofMillis(capped);
    }

    private static Duration retryAfterOf(TelegramApiRequestException e) {
        Integer seconds = e.getParameters() == null ? null : e.getParameters().getRetryAfter();
        if (seconds != null && seconds > 0) {
            return Duration.ofSeconds(seconds);
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
