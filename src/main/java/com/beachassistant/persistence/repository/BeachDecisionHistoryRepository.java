package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.BeachDecisionHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BeachDecisionHistoryRepository extends JpaRepository<BeachDecisionHistoryEntity, Long> {
    Optional<BeachDecisionHistoryEntity> findFirstByBeachIdOrderByGeneratedAtDesc(Long beachId);
}
