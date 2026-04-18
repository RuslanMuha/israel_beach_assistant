package com.beachassistant.telegram.outbox;

import com.beachassistant.telegram.client.TelegramBotSender;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TelegramSenderTest {

    private TelegramOutboxRepository repository;
    private TelegramOutboxProperties properties;
    private ObjectMapper objectMapper;
    private TelegramBotSender fallbackSender;
    private Clock clock;
    private TelegramSender sender;

    @BeforeEach
    void setUp() {
        repository = mock(TelegramOutboxRepository.class);
        properties = new TelegramOutboxProperties();
        properties.setEnabled(true);
        properties.setMaxAttempts(8);
        objectMapper = new ObjectMapper();
        fallbackSender = mock(TelegramBotSender.class);
        clock = Clock.fixed(Instant.parse("2026-04-18T12:00:00Z"), ZoneOffset.UTC);
        sender = new TelegramSender(repository, properties, objectMapper, fallbackSender, clock);
    }

    @Test
    void sendTextEnqueuesPendingEntity() {
        sender.sendText(42L, "Hello");

        ArgumentCaptor<TelegramOutboxEntity> captor = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(captor.capture());
        TelegramOutboxEntity e = captor.getValue();
        assertThat(e.getChatId()).isEqualTo(42L);
        assertThat(e.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(e.getMessageType()).isEqualTo(OutboxMessageType.TEXT);
        assertThat(e.getAttempts()).isZero();
        assertThat(e.getMaxAttempts()).isEqualTo(8);
        assertThat(e.getPayloadJson()).contains("Hello");
        assertThat(e.getNextAttemptAt()).isNotNull();
    }

    @Test
    void sendTextWithKeyboardStoresMarkup() {
        InlineKeyboardMarkup kb = InlineKeyboardMarkup.builder()
                .keyboard(List.of(List.of(
                        InlineKeyboardButton.builder().text("Refresh").callbackData("r").build())))
                .build();
        sender.sendText(9L, "hi", kb);

        ArgumentCaptor<TelegramOutboxEntity> captor = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(captor.capture());
        TelegramOutboxEntity e = captor.getValue();
        assertThat(e.getMessageType()).isEqualTo(OutboxMessageType.TEXT_WITH_KEYBOARD);
        assertThat(e.getPayloadJson()).contains("Refresh");
    }

    @Test
    void dedupKeyHitSkipsEnqueue() {
        when(repository.findByDedupKey("alert:lido:minute42"))
                .thenReturn(Optional.of(new TelegramOutboxEntity()));
        sender.sendTextDeduped(1L, "alert", null, "alert:lido:minute42");
        verify(repository, never()).save(any());
    }

    @Test
    void dedupKeyMissEnqueues() {
        when(repository.findByDedupKey("k1")).thenReturn(Optional.empty());
        sender.sendTextDeduped(1L, "alert", null, "k1");
        ArgumentCaptor<TelegramOutboxEntity> captor = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getDedupKey()).isEqualTo("k1");
    }

    @Test
    void outboxDisabledFallsBackToSynchronousSend() throws Exception {
        properties.setEnabled(false);
        sender.sendText(7L, "direct");
        verify(fallbackSender).execute(any(org.telegram.telegrambots.meta.api.methods.send.SendMessage.class));
        verify(repository, never()).save(any());
    }

    @Test
    void sendPhotoEnqueuesPhotoPayload() {
        sender.sendPhoto(1L, "https://example/img.jpg", "caption", null);
        ArgumentCaptor<TelegramOutboxEntity> captor = ArgumentCaptor.forClass(TelegramOutboxEntity.class);
        verify(repository).save(captor.capture());
        TelegramOutboxEntity e = captor.getValue();
        assertThat(e.getMessageType()).isEqualTo(OutboxMessageType.PHOTO);
        assertThat(e.getPayloadJson()).contains("example/img.jpg");
        assertThat(e.getPayloadJson()).contains("caption");
    }
}
