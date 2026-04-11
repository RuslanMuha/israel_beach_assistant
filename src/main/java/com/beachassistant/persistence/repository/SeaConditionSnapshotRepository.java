package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.SeaConditionSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SeaConditionSnapshotRepository extends JpaRepository<SeaConditionSnapshotEntity, Long> {

    Optional<SeaConditionSnapshotEntity> findTopByBeachIdOrderByCapturedAtDesc(Long beachId);
}
