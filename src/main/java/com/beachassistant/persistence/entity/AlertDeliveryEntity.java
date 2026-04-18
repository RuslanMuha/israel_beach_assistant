package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "alert_delivery",
        uniqueConstraints = @UniqueConstraint(columnNames = {"telegram_user_id", "beach_id", "signature_hash"}))
@Getter
@Setter
@NoArgsConstructor
public class AlertDeliveryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "beach_id", nullable = false)
    private Long beachId;

    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    @Column(name = "sent_at", nullable = false)
    private ZonedDateTime sentAt;
}
