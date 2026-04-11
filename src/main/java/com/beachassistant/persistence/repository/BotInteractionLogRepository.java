package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.BotInteractionLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotInteractionLogRepository extends JpaRepository<BotInteractionLogEntity, Long> {
}
