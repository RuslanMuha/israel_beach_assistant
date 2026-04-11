package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.HealthAdvisorySnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HealthAdvisorySnapshotRepository extends JpaRepository<HealthAdvisorySnapshotEntity, Long> {

    Optional<HealthAdvisorySnapshotEntity> findTopByBeachIdAndActiveTrueOrderByCapturedAtDesc(Long beachId);

    Optional<HealthAdvisorySnapshotEntity> findTopByBeachIdOrderByCapturedAtDesc(Long beachId);
}
