package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.CameraSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CameraSnapshotRepository extends JpaRepository<CameraSnapshotEntity, Long> {

    Optional<CameraSnapshotEntity> findTopByCameraIdOrderByCapturedAtDesc(Long cameraId);
}
