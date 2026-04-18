package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "beach_subscription",
        uniqueConstraints = @UniqueConstraint(columnNames = {"telegram_user_id", "beach_id"}))
@Getter
@Setter
@NoArgsConstructor
public class BeachSubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false)
    private Long telegramUserId;

    @Column(name = "beach_id", nullable = false)
    private Long beachId;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
}
