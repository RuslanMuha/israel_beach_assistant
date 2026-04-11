package com.beachassistant.scheduler;

import com.beachassistant.app.usecase.IngestionUseCase;
import com.beachassistant.common.enums.SourceType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Profile("!test")
public class IngestionScheduler {

    private final IngestionUseCase ingestionUseCase;

    public IngestionScheduler(IngestionUseCase ingestionUseCase) {
        this.ingestionUseCase = ingestionUseCase;
    }

    @Scheduled(fixedRateString = "${beach.scheduler.sea-forecast-rate-ms:1800000}")
    public void ingestSeaForecast() {
        log.info("Scheduled ingestion: SEA_FORECAST");
        ingestionUseCase.ingest(SourceType.SEA_FORECAST);
    }

    @Scheduled(fixedRateString = "${beach.scheduler.advisory-rate-ms:3600000}")
    public void ingestHealthAdvisory() {
        log.info("Scheduled ingestion: HEALTH_ADVISORY");
        ingestionUseCase.ingest(SourceType.HEALTH_ADVISORY);
    }

    @Scheduled(fixedRateString = "${beach.scheduler.jellyfish-rate-ms:7200000}")
    public void ingestJellyfish() {
        log.info("Scheduled ingestion: JELLYFISH");
        ingestionUseCase.ingest(SourceType.JELLYFISH);
    }
}
