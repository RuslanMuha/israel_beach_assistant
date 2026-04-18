package com.beachassistant.telegram.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "telegram_outbox")
@Getter
@Setter
@NoArgsConstructor
public class TelegramOutboxEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_id", nullable = false)
    private Long chatId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false, length = 32)
    private OutboxMessageType messageType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "dedup_key", length = 128)
    private String dedupKey;
}
