package com.beachassistant.telegram.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TelegramOutboxRepository extends JpaRepository<TelegramOutboxEntity, Long> {

    @Query("""
            SELECT o.id FROM TelegramOutboxEntity o
            WHERE o.status = com.beachassistant.telegram.outbox.OutboxStatus.PENDING
              AND o.nextAttemptAt <= :now
            ORDER BY o.nextAttemptAt ASC
            """)
    List<Long> findPendingIds(@Param("now") ZonedDateTime now, org.springframework.data.domain.Pageable pageable);

    @Modifying
    @Query("""
            UPDATE TelegramOutboxEntity o
               SET o.status = com.beachassistant.telegram.outbox.OutboxStatus.IN_FLIGHT,
                   o.attempts = o.attempts + 1
             WHERE o.id = :id
               AND o.status = com.beachassistant.telegram.outbox.OutboxStatus.PENDING
            """)
    int claim(@Param("id") Long id);

    Optional<TelegramOutboxEntity> findByDedupKey(String dedupKey);

    long countByStatus(OutboxStatus status);
}
