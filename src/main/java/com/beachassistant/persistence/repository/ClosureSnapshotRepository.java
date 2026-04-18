package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.ClosureSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClosureSnapshotRepository extends JpaRepository<ClosureSnapshotEntity, Long> {
    Optional<ClosureSnapshotEntity> findFirstByBeachIdOrderByCapturedAtDesc(Long beachId);
}
