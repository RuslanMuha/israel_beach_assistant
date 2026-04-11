package com.beachassistant.scheduler;

import com.beachassistant.app.usecase.IngestionUseCase;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.integration.http.BeachIntegrationProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
public class IngestionScheduler {

    private final IngestionUseCase ingestionUseCase;
    private final IngestionCycleGuard cycleGuard;
    private final BeachIntegrationProperties integrationProperties;
    private final BeachMetrics metrics;

    public IngestionScheduler(IngestionUseCase ingestionUseCase,
                              IngestionCycleGuard cycleGuard,
                              BeachIntegrationProperties integrationProperties,
                              BeachMetrics metrics) {
        this.ingestionUseCase = ingestionUseCase;
        this.cycleGuard = cycleGuard;
        this.integrationProperties = integrationProperties;
        this.metrics = metrics;
    }

    @Scheduled(
            initialDelayString = "${beach.integration.scheduler.initial-delay-ms:90000}",
            fixedDelayString = "${beach.scheduler.sea-forecast-rate-ms:1800000}"
    )
    public void ingestSeaForecast() {
        runIfAble(SourceType.SEA_FORECAST, "SEA_FORECAST");
    }

    @Scheduled(
            initialDelayString = "${beach.integration.scheduler.initial-delay-ms:90000}",
            fixedDelayString = "${beach.scheduler.advisory-rate-ms:3600000}"
    )
    public void ingestHealthAdvisory() {
        runIfAble(SourceType.HEALTH_ADVISORY, "HEALTH_ADVISORY");
    }

    @Scheduled(
            initialDelayString = "${beach.integration.scheduler.initial-delay-ms:90000}",
            fixedDelayString = "${beach.scheduler.jellyfish-rate-ms:7200000}"
    )
    public void ingestJellyfish() {
        runIfAble(SourceType.JELLYFISH, "JELLYFISH");
    }

    private void runIfAble(SourceType sourceType, String label) {
        boolean locked = false;
        if (integrationProperties.getScheduler().isSkipIfOverlap()) {
            if (!cycleGuard.tryBegin(sourceType)) {
                metrics.recordIngestionCycleSkippedOverlap(sourceType);
                log.info("Scheduled ingestion skipped (overlap): {}", label);
                return;
            }
            locked = true;
        }
        try {
            log.info("Scheduled ingestion start: {}", label);
            ingestionUseCase.ingest(sourceType);
        } finally {
            if (locked) {
                cycleGuard.end(sourceType);
            }
        }
    }
}
