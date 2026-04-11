package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "bot_interaction_log")
@Getter
@Setter
@NoArgsConstructor
public class BotInteractionLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "request_type")
    private String requestType;

    @Column(name = "beach_id")
    private Long beachId;

    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;

    @Column(name = "response_status")
    private String responseStatus;

    @Column(name = "latency_ms")
    private Long latencyMs;
}
