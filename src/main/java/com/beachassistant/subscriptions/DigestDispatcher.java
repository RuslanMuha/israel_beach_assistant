package com.beachassistant.subscriptions;

import com.beachassistant.app.usecase.BeachStatusUseCase;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.BeachSubscriptionEntity;
import com.beachassistant.persistence.entity.TelegramUserEntity;
import com.beachassistant.persistence.entity.TelegramUserPreferenceEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.BeachSubscriptionRepository;
import com.beachassistant.persistence.repository.TelegramUserPreferenceRepository;
import com.beachassistant.persistence.repository.TelegramUserRepository;
import com.beachassistant.telegram.outbox.TelegramSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends an opt-in morning summary to subscribed users at their configured {@code digest_hour}.
 * Cron fires every hour on the hour; each run filters subscribers whose local hour matches.
 *
 * <p>Disabled unless {@code beach.subscriptions.enabled=true}.</p>
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "beach.subscriptions", name = "enabled", havingValue = "true")
public class DigestDispatcher {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Jerusalem");

    private final TelegramUserRepository users;
    private final TelegramUserPreferenceRepository preferences;
    private final BeachSubscriptionRepository subscriptions;
    private final BeachRepository beaches;
    private final BeachStatusUseCase statusUseCase;
    private final TelegramSender telegramSender;
    private final Clock clock;

    public DigestDispatcher(TelegramUserRepository users,
                            TelegramUserPreferenceRepository preferences,
                            BeachSubscriptionRepository subscriptions,
                            BeachRepository beaches,
                            BeachStatusUseCase statusUseCase,
                            TelegramSender telegramSender,
                            Clock clock) {
        this.users = users;
        this.preferences = preferences;
        this.subscriptions = subscriptions;
        this.beaches = beaches;
        this.statusUseCase = statusUseCase;
        this.telegramSender = telegramSender;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Jerusalem")
    public void dispatch() {
        int localHour = ZonedDateTime.now(clock.withZone(DEFAULT_ZONE)).getHour();
        List<TelegramUserPreferenceEntity> recipients = preferences.findAll().stream()
                .filter(TelegramUserPreferenceEntity::isDigestEnabled)
                .filter(p -> p.getDigestHour() == localHour)
                .toList();
        for (TelegramUserPreferenceEntity pref : recipients) {
            try {
                sendDigestTo(pref.getTelegramUserId());
            } catch (Exception e) {
                log.warn("Digest failed for user {}: {}", pref.getTelegramUserId(), e.getMessage());
            }
        }
    }

    private void sendDigestTo(Long userPkId) {
        TelegramUserEntity user = users.findById(userPkId).orElse(null);
        if (user == null) return;
        List<BeachSubscriptionEntity> subs = subscriptions.findByTelegramUserId(userPkId);
        if (subs.isEmpty()) return;

        List<String> lines = new ArrayList<>();
        lines.add("🌅 Утренняя сводка");
        for (BeachSubscriptionEntity sub : subs) {
            BeachEntity beach = beaches.findById(sub.getBeachId()).orElse(null);
            if (beach == null) continue;
            try {
                BeachDecision decision = statusUseCase.getStatus(beach.getSlug());
                lines.add("• " + beach.getDisplayName() + ": " + decision.getRecommendation().name());
            } catch (Exception e) {
                lines.add("• " + beach.getDisplayName() + ": нет данных");
            }
        }
        telegramSender.sendText(user.getChatId(), String.join("\n", lines));
    }
}
