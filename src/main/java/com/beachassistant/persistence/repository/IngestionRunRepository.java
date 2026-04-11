package com.beachassistant.persistence.repository;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngestionRunRepository extends JpaRepository<IngestionRunEntity, Long> {

    Optional<IngestionRunEntity> findTopBySourceTypeOrderByStartedAtDesc(SourceType sourceType);
}
