package com.beachassistant.observability;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import com.beachassistant.persistence.repository.IngestionRunRepository;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Composite health indicator that surfaces the last-completed ingestion age and outcome for each
 * critical {@link SourceType}. Exposes {@code DOWN} when any required source has failed and has
 * no successful run in the last {@link #criticalAge}; {@code OUT_OF_SERVICE} when a source is
 * stale; {@code UP} otherwise. Exposed via the existing actuator {@code /health} endpoint.
 */
@Component("beachAssistant")
public class BeachAssistantHealthIndicator implements HealthIndicator {

    private static final Duration STALE_AFTER = Duration.ofHours(6);
    private static final Duration CRITICAL_AFTER = Duration.ofHours(24);

    private final IngestionRunRepository ingestionRunRepository;
    private final Clock clock;

    public BeachAssistantHealthIndicator(IngestionRunRepository ingestionRunRepository, Clock clock) {
        this.ingestionRunRepository = ingestionRunRepository;
        this.clock = clock;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        ZonedDateTime now = ZonedDateTime.now(clock);
        boolean anyCritical = false;
        boolean anyStale = false;
        for (SourceType type : new SourceType[]{SourceType.SEA_FORECAST,
                SourceType.HEALTH_ADVISORY, SourceType.JELLYFISH}) {
            Optional<IngestionRunEntity> lastRun =
                    ingestionRunRepository.findTopBySourceTypeOrderByStartedAtDesc(type);
            Map<String, Object> info = new HashMap<>();
            if (lastRun.isEmpty()) {
                info.put("status", "NEVER_RUN");
                anyCritical = true;
            } else {
                IngestionRunEntity run = lastRun.get();
                Duration age = run.getFinishedAt() != null
                        ? Duration.between(run.getFinishedAt(), now)
                        : Duration.between(run.getStartedAt(), now);
                info.put("status", run.getStatus());
                info.put("ageSeconds", age.toSeconds());
                info.put("finishedAt", run.getFinishedAt());
                if (age.compareTo(CRITICAL_AFTER) > 0) {
                    anyCritical = true;
                } else if (age.compareTo(STALE_AFTER) > 0) {
                    anyStale = true;
                }
            }
            details.put(type.name(), info);
        }
        if (anyCritical) {
            return Health.down().withDetails(details).build();
        }
        if (anyStale) {
            return Health.status("OUT_OF_SERVICE").withDetails(details).build();
        }
        return Health.up().withDetails(details).build();
    }
}
