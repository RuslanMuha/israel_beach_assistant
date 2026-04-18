package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "telegram_user_preference")
@Getter
@Setter
@NoArgsConstructor
public class TelegramUserPreferenceEntity {

    @Id
    @Column(name = "telegram_user_id")
    private Long telegramUserId;

    @Column(name = "digest_enabled", nullable = false)
    private boolean digestEnabled = false;

    @Column(name = "digest_hour", nullable = false)
    private short digestHour = 6;

    @Column(name = "quiet_hours_start")
    private Short quietHoursStart;

    @Column(name = "quiet_hours_end")
    private Short quietHoursEnd;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
