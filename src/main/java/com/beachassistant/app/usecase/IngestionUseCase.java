package com.beachassistant.app.usecase;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.persistence.entity.*;
import com.beachassistant.persistence.repository.*;
import com.beachassistant.source.advisory.HealthAdvisoryAdapter;
import com.beachassistant.source.advisory.HealthAdvisoryRecord;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceRequest;
import com.beachassistant.source.jellyfish.JellyfishAdapter;
import com.beachassistant.source.jellyfish.JellyfishRecord;
import com.beachassistant.source.sea.SeaForecastAdapter;
import com.beachassistant.source.sea.SeaForecastRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
public class IngestionUseCase {

    private final BeachRepository beachRepository;
    private final SeaConditionSnapshotRepository seaRepo;
    private final HealthAdvisorySnapshotRepository advisoryRepo;
    private final JellyfishReportAggregateRepository jellyfishRepo;
    private final IngestionRunRepository ingestionRunRepo;
    private final SeaForecastAdapter seaAdapter;
    private final HealthAdvisoryAdapter advisoryAdapter;
    private final JellyfishAdapter jellyfishAdapter;
    private final CacheManager cacheManager;
    private final BeachMetrics metrics;

    public IngestionUseCase(BeachRepository beachRepository,
                             SeaConditionSnapshotRepository seaRepo,
                             HealthAdvisorySnapshotRepository advisoryRepo,
                             JellyfishReportAggregateRepository jellyfishRepo,
                             IngestionRunRepository ingestionRunRepo,
                             SeaForecastAdapter seaAdapter,
                             HealthAdvisoryAdapter advisoryAdapter,
                             JellyfishAdapter jellyfishAdapter,
                             CacheManager cacheManager,
                             BeachMetrics metrics) {
        this.beachRepository = beachRepository;
        this.seaRepo = seaRepo;
        this.advisoryRepo = advisoryRepo;
        this.jellyfishRepo = jellyfishRepo;
        this.ingestionRunRepo = ingestionRunRepo;
        this.seaAdapter = seaAdapter;
        this.advisoryAdapter = advisoryAdapter;
        this.jellyfishAdapter = jellyfishAdapter;
        this.cacheManager = cacheManager;
        this.metrics = metrics;
    }

    @Transactional
    public IngestionRunEntity ingest(SourceType sourceType) {
        IngestionRunEntity run = new IngestionRunEntity();
        run.setSourceType(sourceType);
        run.setStartedAt(ZonedDateTime.now());
        run.setStatus("RUNNING");
        ingestionRunRepo.save(run);

        int fetched = 0;
        int saved = 0;
        String error = null;

        try {
            List<BeachEntity> beaches = beachRepository.findAllByActiveTrue();
            for (BeachEntity beach : beaches) {
                SourceRequest req = SourceRequest.builder()
                        .beachSlug(beach.getSlug())
                        .build();

                switch (sourceType) {
                    case SEA_FORECAST -> {
                        FetchResult<SeaForecastRecord> result = seaAdapter.fetch(req);
                        fetched += result.getRecords().size();
                        for (SeaForecastRecord r : result.getRecords()) {
                            SeaConditionSnapshotEntity entity = mapSeaRecord(r, beach);
                            seaRepo.save(entity);
                            saved++;
                        }
                    }
                    case HEALTH_ADVISORY -> {
                        FetchResult<HealthAdvisoryRecord> result = advisoryAdapter.fetch(req);
                        fetched += result.getRecords().size();
                        for (HealthAdvisoryRecord r : result.getRecords()) {
                            HealthAdvisorySnapshotEntity entity = mapAdvisoryRecord(r, beach);
                            advisoryRepo.save(entity);
                            saved++;
                        }
                    }
                    case JELLYFISH -> {
                        FetchResult<JellyfishRecord> result = jellyfishAdapter.fetch(req);
                        fetched += result.getRecords().size();
                        for (JellyfishRecord r : result.getRecords()) {
                            JellyfishReportAggregateEntity entity = mapJellyfishRecord(r, beach);
                            jellyfishRepo.save(entity);
                            saved++;
                        }
                    }
                    default -> log.warn("Ingestion not implemented for sourceType={}", sourceType);
                }
            }

            evictCaches();
            run.setStatus("SUCCESS");
            metrics.recordSourceFetch(sourceType, true);
        } catch (Exception e) {
            log.error("Ingestion failed for sourceType={}: {}", sourceType, e.getMessage(), e);
            error = e.getMessage();
            run.setStatus("FAILED");
            run.setErrorSummary(error);
            metrics.recordSourceFetch(sourceType, false);
        }

        run.setFinishedAt(ZonedDateTime.now());
        run.setRecordsFetched(fetched);
        run.setRecordsSaved(saved);
        return ingestionRunRepo.save(run);
    }

    private void evictCaches() {
        var statusCache = cacheManager.getCache("beachStatus");
        if (statusCache != null) {
            statusCache.clear();
        }
    }

    private SeaConditionSnapshotEntity mapSeaRecord(SeaForecastRecord r, BeachEntity beach) {
        SeaConditionSnapshotEntity e = new SeaConditionSnapshotEntity();
        e.setBeach(beach);
        e.setSourceType(SourceType.SEA_FORECAST);
        e.setCapturedAt(r.getCapturedAt());
        e.setValidFrom(r.getValidFrom());
        e.setValidTo(r.getValidTo());
        e.setSeaRiskLevel(r.getSeaRiskLevel());
        e.setWaveHeightM(r.getWaveHeightM());
        e.setWindSpeedMps(r.getWindSpeedMps());
        e.setWindDirection(r.getWindDirection());
        e.setAirTemperatureC(r.getAirTemperatureC());
        e.setRelativeHumidityPct(r.getRelativeHumidityPct());
        e.setUvIndex(r.getUvIndex());
        e.setSeaTemperatureC(r.getSeaTemperatureC());
        e.setRawPayloadJson(r.getRawPayloadJson());
        e.setIntervalIsInferred(r.isIntervalIsInferred());
        return e;
    }

    private HealthAdvisorySnapshotEntity mapAdvisoryRecord(HealthAdvisoryRecord r, BeachEntity beach) {
        HealthAdvisorySnapshotEntity e = new HealthAdvisorySnapshotEntity();
        e.setBeach(beach);
        e.setSourceType(SourceType.HEALTH_ADVISORY);
        e.setCapturedAt(r.getCapturedAt());
        e.setValidFrom(r.getValidFrom());
        e.setValidTo(r.getValidTo());
        e.setActive(r.isActive());
        e.setAdvisoryType(r.getAdvisoryType());
        e.setMessage(r.getMessage());
        e.setRawPayloadJson(r.getRawPayloadJson());
        return e;
    }

    private JellyfishReportAggregateEntity mapJellyfishRecord(JellyfishRecord r, BeachEntity beach) {
        JellyfishReportAggregateEntity e = new JellyfishReportAggregateEntity();
        e.setBeach(beach);
        e.setSourceType(SourceType.JELLYFISH);
        e.setCapturedAt(r.getCapturedAt());
        e.setWindowStart(r.getWindowStart());
        e.setWindowEnd(r.getWindowEnd());
        e.setSeverityLevel(r.getSeverityLevel());
        e.setReportCount(r.getReportCount());
        e.setConfidenceLevel(r.getConfidenceLevel());
        e.setRawPayloadJson(r.getRawPayloadJson());
        return e;
    }
}
