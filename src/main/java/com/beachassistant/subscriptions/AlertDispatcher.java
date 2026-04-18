package com.beachassistant.subscriptions;

import com.beachassistant.app.usecase.BeachStatusUseCase;
import com.beachassistant.common.enums.ReasonCode;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.persistence.entity.AlertDeliveryEntity;
import com.beachassistant.persistence.entity.BeachDecisionHistoryEntity;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.BeachSubscriptionEntity;
import com.beachassistant.persistence.entity.TelegramUserEntity;
import com.beachassistant.persistence.repository.AlertDeliveryRepository;
import com.beachassistant.persistence.repository.BeachDecisionHistoryRepository;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.BeachSubscriptionRepository;
import com.beachassistant.persistence.repository.TelegramUserRepository;
import com.beachassistant.telegram.outbox.TelegramSender;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Polls every 60 seconds, recomputes decisions for subscribed beaches, compares a stable
 * signature against the latest row in {@code beach_decision_history}, and enqueues alerts for
 * each subscriber on change. Uses a Postgres advisory lock so multiple replicas can coexist
 * safely (only one will hold the lock at a time).
 *
 * <p>Disabled unless {@code beach.subscriptions.enabled=true} so the full workflow can ship
 * dark until the feature flag is flipped.</p>
 */
@Slf4j
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "beach.subscriptions", name = "enabled", havingValue = "true")
public class AlertDispatcher {

    private static final long ADVISORY_LOCK_KEY = 5_417_309_451L; // stable, random

    private final BeachSubscriptionRepository subscriptions;
    private final BeachRepository beaches;
    private final BeachDecisionHistoryRepository history;
    private final AlertDeliveryRepository deliveries;
    private final TelegramUserRepository users;
    private final BeachStatusUseCase statusUseCase;
    private final TelegramSender telegramSender;
    private final EntityManager entityManager;
    private final Clock clock;

    public AlertDispatcher(BeachSubscriptionRepository subscriptions,
                           BeachRepository beaches,
                           BeachDecisionHistoryRepository history,
                           AlertDeliveryRepository deliveries,
                           TelegramUserRepository users,
                           BeachStatusUseCase statusUseCase,
                           TelegramSender telegramSender,
                           EntityManager entityManager,
                           Clock clock) {
        this.subscriptions = subscriptions;
        this.beaches = beaches;
        this.history = history;
        this.deliveries = deliveries;
        this.users = users;
        this.statusUseCase = statusUseCase;
        this.telegramSender = telegramSender;
        this.entityManager = entityManager;
        this.clock = clock;
    }

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    @Transactional
    public void dispatch() {
        if (!acquireAdvisoryLock()) {
            log.debug("Alert dispatch skipped: another instance holds the advisory lock.");
            return;
        }
        try {
            List<BeachEntity> watchedBeaches = beaches.findAllByActiveTrue().stream()
                    .filter(b -> !subscriptions.findByBeachId(b.getId()).isEmpty())
                    .toList();
            for (BeachEntity beach : watchedBeaches) {
                try {
                    dispatchForBeach(beach);
                } catch (Exception e) {
                    log.warn("Alert dispatch failed for beach {}: {}", beach.getSlug(), e.getMessage());
                }
            }
        } finally {
            releaseAdvisoryLock();
        }
    }

    private void dispatchForBeach(BeachEntity beach) {
        BeachDecision decision = statusUseCase.getStatus(beach.getSlug());
        String signature = signature(decision);
        Optional<BeachDecisionHistoryEntity> last =
                history.findFirstByBeachIdOrderByGeneratedAtDesc(beach.getId());
        if (last.isPresent() && last.get().getSignatureHash().equals(signature)) {
            return;
        }
        BeachDecisionHistoryEntity row = new BeachDecisionHistoryEntity();
        row.setBeachId(beach.getId());
        row.setRecommendation(decision.getRecommendation().name());
        row.setReasonCodes(reasonCodesText(decision));
        row.setFreshnessBucket(freshnessBucket(decision));
        row.setSignatureHash(signature);
        row.setGeneratedAt(ZonedDateTime.now(clock));
        history.save(row);

        List<BeachSubscriptionEntity> subs = subscriptions.findByBeachId(beach.getId());
        for (BeachSubscriptionEntity sub : subs) {
            if (deliveries.existsByTelegramUserIdAndBeachIdAndSignatureHash(
                    sub.getTelegramUserId(), beach.getId(), signature)) {
                continue;
            }
            TelegramUserEntity user = users.findById(sub.getTelegramUserId()).orElse(null);
            if (user == null) continue;
            String text = "🔔 Обновление по пляжу " + beach.getDisplayName()
                    + ": " + decision.getRecommendation().name();
            String dedup = "alert:" + beach.getId() + ":" + signature + ":" + user.getId();
            telegramSender.sendTextDeduped(user.getChatId(), text, null, dedup);

            AlertDeliveryEntity delivery = new AlertDeliveryEntity();
            delivery.setTelegramUserId(user.getId());
            delivery.setBeachId(beach.getId());
            delivery.setSignatureHash(signature);
            delivery.setSentAt(ZonedDateTime.now(clock));
            deliveries.save(delivery);
        }
    }

    private static String reasonCodesText(BeachDecision decision) {
        return decision.getReasonCodes().stream()
                .map(ReasonCode::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String freshnessBucket(BeachDecision decision) {
        // Coarse bucket keeps the signature stable through small freshness jitter.
        return decision.getConfidence() != null ? decision.getConfidence().name() : "UNKNOWN";
    }

    private static String signature(BeachDecision decision) {
        String raw = decision.getRecommendation().name() + "|"
                + reasonCodesText(decision) + "|"
                + freshnessBucket(decision);
        return sha256Hex(raw);
    }

    private static String sha256Hex(String s) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(s.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean acquireAdvisoryLock() {
        try {
            Object val = entityManager
                    .createNativeQuery("SELECT pg_try_advisory_lock(?)")
                    .setParameter(1, ADVISORY_LOCK_KEY)
                    .getSingleResult();
            return Boolean.TRUE.equals(val);
        } catch (Exception e) {
            // H2 / in-memory databases in tests don't support pg_try_advisory_lock; treat as held.
            return true;
        }
    }

    private void releaseAdvisoryLock() {
        try {
            entityManager.createNativeQuery("SELECT pg_advisory_unlock(?)")
                    .setParameter(1, ADVISORY_LOCK_KEY)
                    .getSingleResult();
        } catch (Exception ignored) {
            // Non-Postgres: no-op
        }
    }
}
