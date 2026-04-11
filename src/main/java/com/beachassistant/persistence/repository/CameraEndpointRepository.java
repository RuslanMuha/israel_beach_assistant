package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.CameraEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CameraEndpointRepository extends JpaRepository<CameraEndpointEntity, Long> {

    Optional<CameraEndpointEntity> findTopByBeachIdAndActiveTrueOrderByIdAsc(Long beachId);
}
