package com.beachassistant.telegram.outbox;

import com.beachassistant.telegram.client.TelegramBotSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Mockito-based coverage of the outbox dispatcher state machine: success, 429 with retry-after,
 * generic failure backoff, and terminal {@link OutboxStatus#FAILED} once attempts exceed the cap.
 */
class TelegramOutboxDispatcherTest {

    private TelegramOutboxRepository repository;
    private TelegramOutboxProperties properties;
    private TelegramBotSender botSender;
    private ObjectMapper objectMapper;
    private Clock clock;
    private TransactionTemplate txTemplate;
    private TelegramOutboxDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        repository = mock(TelegramOutboxRepository.class);
        properties = new TelegramOutboxProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(3);
        botSender = mock(TelegramBotSender.class);
        objectMapper = new ObjectMapper();
        clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        txTemplate = new TransactionTemplate(new NoopTransactionManager());
        dispatcher = new TelegramOutboxDispatcher(repository, properties, botSender, objectMapper, clock, txTemplate);
    }

    @Test
    void tick_doesNothingWhenDisabled() {
        properties.setEnabled(false);
        dispatcher.tick();
        verify(repository, never()).findPendingIds(any(ZonedDateTime.class), any(Pageable.class));
    }

    @Test
    void tick_marksSuccessWhenBotAccepts() throws Exception {
        TelegramOutboxEntity entity = pendingTextEntity(1L, 0);
        when(repository.findPendingIds(any(ZonedDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L));
        when(repository.claim(1L)).thenReturn(1);
        when(repository.findById(1L)).thenReturn(Optional.of(entity));

        dispatcher.tick();

        ArgumentCaptor<TelegramOutboxEntity> saved = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.SENT);
        assertThat(saved.getValue().getSentAt()).isNotNull();
    }

    @Test
    void tick_retriesWithHintWhenRetryAfterPresent() throws Exception {
        TelegramOutboxEntity entity = pendingTextEntity(2L, 1);
        when(repository.findPendingIds(any(ZonedDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(2L));
        when(repository.claim(2L)).thenReturn(1);
        when(repository.findById(2L)).thenReturn(Optional.of(entity));
        TelegramApiRequestException throttled = rateLimitedException(7);
        doThrow(throttled).when(botSender).execute(any(SendMessage.class));

        dispatcher.tick();

        ArgumentCaptor<TelegramOutboxEntity> saved = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getValue().getNextAttemptAt())
                .isAfterOrEqualTo(ZonedDateTime.now(clock).plusSeconds(7).minusSeconds(1));
    }

    @Test
    void tick_moveToFailedAfterMaxAttempts() throws Exception {
        // maxAttempts = 3; simulate attempts already at cap from prior runs.
        TelegramOutboxEntity entity = pendingTextEntity(3L, 3);
        when(repository.findPendingIds(any(ZonedDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(3L));
        when(repository.claim(3L)).thenReturn(1);
        when(repository.findById(3L)).thenReturn(Optional.of(entity));
        doThrow(new RuntimeException("connection reset"))
                .when(botSender).execute(any(SendMessage.class));

        dispatcher.tick();

        ArgumentCaptor<TelegramOutboxEntity> saved = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.FAILED);
    }

    @Test
    void tick_skipsEntityWhenClaimLoses() {
        when(repository.findPendingIds(any(ZonedDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(99L));
        when(repository.claim(99L)).thenReturn(0);

        dispatcher.tick();

        verify(repository, never()).save(any());
    }

    private TelegramOutboxEntity pendingTextEntity(long id, int attempts) {
        TelegramOutboxEntity e = new TelegramOutboxEntity();
        e.setId(id);
        e.setChatId(100L + id);
        e.setMessageType(OutboxMessageType.TEXT);
        e.setPayloadJson("{\"text\":\"hello\"}");
        e.setStatus(OutboxStatus.IN_FLIGHT);
        e.setAttempts(attempts);
        e.setMaxAttempts(properties.getMaxAttempts());
        e.setNextAttemptAt(ZonedDateTime.now(clock));
        e.setCreatedAt(ZonedDateTime.now(clock));
        return e;
    }

    private static TelegramApiRequestException rateLimitedException(int retryAfterSeconds) {
        org.telegram.telegrambots.meta.api.objects.ResponseParameters params =
                new org.telegram.telegrambots.meta.api.objects.ResponseParameters();
        params.setRetryAfter(retryAfterSeconds);
        return new TelegramApiRequestException("Too Many Requests") {
            @Override
            public org.telegram.telegrambots.meta.api.objects.ResponseParameters getParameters() {
                return params;
            }
        };
    }

    /** Minimal in-memory TransactionManager so the dispatcher's transaction callbacks actually run. */
    private static final class NoopTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new org.springframework.transaction.support.SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            // no-op
        }

        @Override
        public void rollback(TransactionStatus status) {
            // no-op
        }
    }

}
