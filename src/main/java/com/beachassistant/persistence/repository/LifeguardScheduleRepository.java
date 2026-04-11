package com.beachassistant.persistence.repository;

import com.beachassistant.persistence.entity.LifeguardScheduleEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface LifeguardScheduleRepository extends JpaRepository<LifeguardScheduleEntity, Long> {

    @Query("""
            SELECT s FROM LifeguardScheduleEntity s
            WHERE s.beach.id = :beachId
              AND s.active = true
              AND (s.effectiveFrom IS NULL OR s.effectiveFrom <= :today)
              AND (s.effectiveTo IS NULL OR s.effectiveTo >= :today)
            ORDER BY s.scheduleType DESC, s.capturedAt DESC
            """)
    List<LifeguardScheduleEntity> findActiveSchedulesForDate(
            @Param("beachId") Long beachId,
            @Param("today") LocalDate today);
}
