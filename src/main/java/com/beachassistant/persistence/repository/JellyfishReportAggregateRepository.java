package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.JellyfishReportAggregateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JellyfishReportAggregateRepository extends JpaRepository<JellyfishReportAggregateEntity, Long> {

    Optional<JellyfishReportAggregateEntity> findTopByBeachIdOrderByCapturedAtDesc(Long beachId);
}
