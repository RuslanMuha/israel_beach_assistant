package com.beachassistant.telegram.outbox;

import com.beachassistant.telegram.client.TelegramBotSender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.ActionType;
import org.telegram.telegrambots.meta.api.methods.send.SendChatAction;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.time.Clock;
import java.time.ZonedDateTime;

/**
 * Durable Telegram send facade. Callers always return immediately after enqueue;
 * {@link TelegramOutboxDispatcher} performs actual delivery with retry.
 */
@Slf4j
@Service
public class TelegramSender {

    private final TelegramOutboxRepository repository;
    private final TelegramOutboxProperties properties;
    private final ObjectMapper objectMapper;
    private final TelegramBotSender fallbackSender;
    private final Clock clock;

    public TelegramSender(TelegramOutboxRepository repository,
                          TelegramOutboxProperties properties,
                          ObjectMapper objectMapper,
                          TelegramBotSender fallbackSender,
                          Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.fallbackSender = fallbackSender;
        this.clock = clock;
    }

    public void sendText(long chatId, String text) {
        enqueue(chatId, OutboxMessageType.TEXT, OutboxPayload.text(text, null, null), null);
    }

    public void sendText(long chatId, String text, InlineKeyboardMarkup keyboard) {
        enqueue(chatId, OutboxMessageType.TEXT_WITH_KEYBOARD,
                OutboxPayload.text(text, null, toJson(keyboard)), null);
    }

    public void sendPhoto(long chatId, String photoUrl, String caption, InlineKeyboardMarkup keyboard) {
        enqueue(chatId, OutboxMessageType.PHOTO,
                OutboxPayload.photo(photoUrl, caption, toJson(keyboard)), null);
    }

    public void sendChatAction(long chatId, ActionType action) {
        // Chat actions are time-sensitive hints (typing…); bypass outbox to avoid staleness.
        try {
            SendChatAction chatAction = new SendChatAction();
            chatAction.setChatId(chatId);
            chatAction.setAction(action);
            fallbackSender.execute(chatAction);
        } catch (TelegramApiException e) {
            log.debug("sendChatAction failed (ignored): {}", e.getMessage());
        }
    }

    /**
     * Enqueue with a dedup key — subsequent calls with the same key are coalesced into the first row.
     * Useful for "status card for beach X to chat Y within minute Z" alerts.
     */
    public void sendTextDeduped(long chatId, String text, InlineKeyboardMarkup keyboard, String dedupKey) {
        enqueue(chatId, OutboxMessageType.TEXT_WITH_KEYBOARD,
                OutboxPayload.text(text, null, toJson(keyboard)), dedupKey);
    }

    protected void enqueue(long chatId, OutboxMessageType type, OutboxPayload payload, String dedupKey) {
        if (!properties.isEnabled()) {
            sendSynchronouslyBestEffort(chatId, type, payload);
            return;
        }
        if (dedupKey != null && repository.findByDedupKey(dedupKey).isPresent()) {
            log.debug("Outbox dedup hit for key={}", dedupKey);
            return;
        }
        TelegramOutboxEntity entity = new TelegramOutboxEntity();
        entity.setChatId(chatId);
        entity.setMessageType(type);
        entity.setStatus(OutboxStatus.PENDING);
        entity.setAttempts(0);
        entity.setMaxAttempts(properties.getMaxAttempts());
        entity.setNextAttemptAt(ZonedDateTime.now(clock));
        entity.setCreatedAt(ZonedDateTime.now(clock));
        entity.setDedupKey(dedupKey);
        try {
            entity.setPayloadJson(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Telegram payload", e);
        }
        repository.save(entity);
    }

    private void sendSynchronouslyBestEffort(long chatId, OutboxMessageType type, OutboxPayload payload) {
        try {
            switch (type) {
                case TEXT, TEXT_WITH_KEYBOARD -> {
                    SendMessage.SendMessageBuilder b = SendMessage.builder().chatId(chatId).text(payload.text());
                    if (payload.replyMarkup() != null) {
                        b.replyMarkup(objectMapper.treeToValue(payload.replyMarkup(), InlineKeyboardMarkup.class));
                    }
                    fallbackSender.execute(b.build());
                }
                case PHOTO -> {
                    SendPhoto.SendPhotoBuilder b = SendPhoto.builder()
                            .chatId(chatId).photo(new InputFile(payload.photoUrl()));
                    if (payload.caption() != null) b.caption(payload.caption());
                    if (payload.replyMarkup() != null) {
                        b.replyMarkup(objectMapper.treeToValue(payload.replyMarkup(), InlineKeyboardMarkup.class));
                    }
                    fallbackSender.execute(b.build());
                }
                case CHAT_ACTION -> { /* handled via dedicated method */ }
            }
        } catch (Exception e) {
            log.warn("Synchronous Telegram send failed (outbox disabled): {}", e.getMessage());
        }
    }

    private JsonNode toJson(InlineKeyboardMarkup keyboard) {
        if (keyboard == null) {
            return null;
        }
        return objectMapper.valueToTree(keyboard);
    }
}
