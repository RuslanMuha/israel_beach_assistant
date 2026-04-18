package com.beachassistant.subscriptions;

import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.BeachSubscriptionEntity;
import com.beachassistant.persistence.entity.TelegramUserEntity;
import com.beachassistant.persistence.entity.TelegramUserPreferenceEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.BeachSubscriptionRepository;
import com.beachassistant.persistence.repository.TelegramUserPreferenceRepository;
import com.beachassistant.persistence.repository.TelegramUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Transactional façade for user subscriptions: upserts {@link TelegramUserEntity} on first
 * interaction, and manages {@link BeachSubscriptionEntity} rows. Chat-level command handlers
 * call this service rather than the repositories directly so consistency stays in one place.
 */
@Service
public class SubscriptionService {

    private final TelegramUserRepository users;
    private final TelegramUserPreferenceRepository preferences;
    private final BeachSubscriptionRepository subscriptions;
    private final BeachRepository beaches;
    private final Clock clock;

    public SubscriptionService(TelegramUserRepository users,
                               TelegramUserPreferenceRepository preferences,
                               BeachSubscriptionRepository subscriptions,
                               BeachRepository beaches,
                               Clock clock) {
        this.users = users;
        this.preferences = preferences;
        this.subscriptions = subscriptions;
        this.beaches = beaches;
        this.clock = clock;
    }

    @Transactional
    public TelegramUserEntity upsertUser(long telegramUserId, long chatId, String languageCode) {
        ZonedDateTime now = ZonedDateTime.now(clock);
        TelegramUserEntity user = users.findByTelegramUserId(telegramUserId)
                .orElseGet(() -> {
                    TelegramUserEntity u = new TelegramUserEntity();
                    u.setTelegramUserId(telegramUserId);
                    u.setCreatedAt(now);
                    return u;
                });
        user.setChatId(chatId);
        user.setLanguageCode(languageCode);
        user.setUpdatedAt(now);
        return users.save(user);
    }

    @Transactional
    public Result subscribe(long telegramUserId, long chatId, String languageCode, String beachSlug) {
        TelegramUserEntity user = upsertUser(telegramUserId, chatId, languageCode);
        BeachEntity beach = beaches.findBySlugAndActiveTrue(beachSlug)
                .orElseThrow(() -> new BeachNotFoundException(beachSlug));
        Optional<BeachSubscriptionEntity> existing =
                subscriptions.findByTelegramUserIdAndBeachId(user.getId(), beach.getId());
        if (existing.isPresent()) {
            return new Result(user, beach, false);
        }
        BeachSubscriptionEntity row = new BeachSubscriptionEntity();
        row.setTelegramUserId(user.getId());
        row.setBeachId(beach.getId());
        row.setCreatedAt(ZonedDateTime.now(clock));
        subscriptions.save(row);
        return new Result(user, beach, true);
    }

    @Transactional
    public boolean unsubscribe(long telegramUserId, String beachSlug) {
        Optional<TelegramUserEntity> user = users.findByTelegramUserId(telegramUserId);
        Optional<BeachEntity> beach = beaches.findBySlugAndActiveTrue(beachSlug);
        if (user.isEmpty() || beach.isEmpty()) {
            return false;
        }
        return subscriptions.findByTelegramUserIdAndBeachId(user.get().getId(), beach.get().getId())
                .map(row -> {
                    subscriptions.delete(row);
                    return true;
                })
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<BeachSubscriptionEntity> subscriptionsFor(long telegramUserId) {
        return users.findByTelegramUserId(telegramUserId)
                .map(u -> subscriptions.findByTelegramUserId(u.getId()))
                .orElse(List.of());
    }

    @Transactional
    public void setDigestEnabled(long telegramUserId, long chatId, String languageCode, boolean enabled) {
        TelegramUserEntity user = upsertUser(telegramUserId, chatId, languageCode);
        TelegramUserPreferenceEntity pref = preferences.findById(user.getId())
                .orElseGet(() -> {
                    TelegramUserPreferenceEntity p = new TelegramUserPreferenceEntity();
                    p.setTelegramUserId(user.getId());
                    return p;
                });
        pref.setDigestEnabled(enabled);
        pref.setUpdatedAt(ZonedDateTime.now(clock));
        preferences.save(pref);
    }

    public record Result(TelegramUserEntity user, BeachEntity beach, boolean created) {
    }
}
