package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.BeachSubscriptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeachSubscriptionRepository extends JpaRepository<BeachSubscriptionEntity, Long> {
    List<BeachSubscriptionEntity> findByTelegramUserId(Long telegramUserId);

    List<BeachSubscriptionEntity> findByBeachId(Long beachId);

    Optional<BeachSubscriptionEntity> findByTelegramUserIdAndBeachId(Long telegramUserId, Long beachId);
}
