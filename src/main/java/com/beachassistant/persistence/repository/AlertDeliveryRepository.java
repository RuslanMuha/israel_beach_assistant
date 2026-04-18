package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.AlertDeliveryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDeliveryRepository extends JpaRepository<AlertDeliveryEntity, Long> {
    boolean existsByTelegramUserIdAndBeachIdAndSignatureHash(Long telegramUserId, Long beachId, String hash);
}
