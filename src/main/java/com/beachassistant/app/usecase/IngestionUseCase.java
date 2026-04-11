package com.beachassistant.app.usecase;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.integration.http.BeachIntegrationProperties;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class IngestionUseCase {

    private static final String STALE_HTTP = "STALE_HTTP_FALLBACK";

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
    private final TransactionTemplate transactionTemplate;
    private final BeachIntegrationProperties integrationProperties;
    private final AsyncTaskExecutor beachIngestionExecutor;

    public IngestionUseCase(BeachRepository beachRepository,
                             SeaConditionSnapshotRepository seaRepo,
                             HealthAdvisorySnapshotRepository advisoryRepo,
                             JellyfishReportAggregateRepository jellyfishRepo,
                             IngestionRunRepository ingestionRunRepo,
                             SeaForecastAdapter seaAdapter,
                             HealthAdvisoryAdapter advisoryAdapter,
                             JellyfishAdapter jellyfishAdapter,
                             CacheManager cacheManager,
                             BeachMetrics metrics,
                             PlatformTransactionManager transactionManager,
                             BeachIntegrationProperties integrationProperties,
                             @Qualifier("beachIngestionExecutor") AsyncTaskExecutor beachIngestionExecutor) {
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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.integrationProperties = integrationProperties;
        this.beachIngestionExecutor = beachIngestionExecutor;
    }

    public IngestionRunEntity ingest(SourceType sourceType) {
        long t0 = System.nanoTime();
        IngestionRunEntity run = new IngestionRunEntity();
        run.setSourceType(sourceType);
        run.setStartedAt(ZonedDateTime.now());
        run.setStatus("RUNNING");
        ingestionRunRepo.save(run);

        AtomicInteger fetched = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger staleHttp = new AtomicInteger();

        int beachesTotal = 0;
        try {
            List<BeachEntity> beaches = beachRepository.findAllByActiveTrue();
            beachesTotal = beaches.size();
            int batchSize = Math.max(1, integrationProperties.getIngestion().getBatchSize());

            List<List<BeachEntity>> batches = partition(beaches, batchSize);
            for (List<BeachEntity> batch : batches) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (BeachEntity beach : batch) {
                    futures.add(CompletableFuture.runAsync(() ->
                            transactionTemplate.executeWithoutResult(status ->
                                    ingestSingleBeach(beach, sourceType, fetched, saved, failures, staleHttp)),
                            beachIngestionExecutor));
                }
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            }

            evictCaches();
            int failCount = failures.get();
            if (failCount == 0) {
                run.setStatus("SUCCESS");
            } else if (saved.get() > 0 || fetched.get() > 0) {
                run.setStatus("PARTIAL");
            } else {
                run.setStatus("FAILED");
            }
            metrics.recordSourceFetch(sourceType, failCount == 0);
            String summary = "ingestion_cycle source=%s beaches=%d saved_records=%d fetched=%d failures=%d stale_http_fallback=%d duration_ms=%d"
                    .formatted(sourceType, beachesTotal, saved.get(), fetched.get(), failCount, staleHttp.get(),
                            (System.nanoTime() - t0) / 1_000_000L);
            run.setErrorSummary(failCount > 0 ? summary : null);
            if (failCount > 0) {
                log.warn("{}", summary);
            } else {
                log.info("{}", summary);
            }
        } catch (Exception e) {
            log.error("Ingestion coordinator failed for sourceType={}: {}", sourceType, e.getMessage(), e);
            run.setStatus("FAILED");
            run.setErrorSummary(e.getMessage());
            metrics.recordSourceFetch(sourceType, false);
        }

        run.setFinishedAt(ZonedDateTime.now());
        run.setRecordsFetched(fetched.get());
        run.setRecordsSaved(saved.get());
        metrics.recordSourceFetchDuration(sourceType, (System.nanoTime() - t0) / 1_000_000L);
        return ingestionRunRepo.save(run);
    }

    private void ingestSingleBeach(BeachEntity beach,
                                   SourceType sourceType,
                                   AtomicInteger fetched,
                                   AtomicInteger saved,
                                   AtomicInteger failures,
                                   AtomicInteger staleHttp) {
        SourceRequest req = SourceRequest.builder()
                .beachSlug(beach.getSlug())
                .build();
        try {
            switch (sourceType) {
                case SEA_FORECAST -> handleSea(beach, req, fetched, saved, failures, staleHttp);
                case HEALTH_ADVISORY -> handleAdvisory(beach, req, fetched, saved, failures, staleHttp);
                case JELLYFISH -> handleJellyfish(beach, req, fetched, saved, failures, staleHttp);
                default -> log.warn("Ingestion not implemented for sourceType={}", sourceType);
            }
        } catch (Exception e) {
            failures.incrementAndGet();
            metrics.recordIngestionBeach(sourceType, "failed");
            log.warn("Ingestion beach failed slug={} source={}: {}", beach.getSlug(), sourceType, e.getMessage());
        }
    }

    private void handleSea(BeachEntity beach,
                           SourceRequest req,
                           AtomicInteger fetched,
                           AtomicInteger saved,
                           AtomicInteger failures,
                           AtomicInteger staleHttp) {
        FetchResult<SeaForecastRecord> result = seaAdapter.fetch(req);
        if (!result.isSuccess()) {
            failures.incrementAndGet();
            metrics.recordIngestionBeach(SourceType.SEA_FORECAST, "failed");
            return;
        }
        if (containsStale(result)) {
            staleHttp.incrementAndGet();
            metrics.recordIngestionStaleFallback(SourceType.SEA_FORECAST);
        }
        fetched.addAndGet(result.getRecords().size());
        for (SeaForecastRecord r : result.getRecords()) {
            SeaConditionSnapshotEntity entity = mapSeaRecord(r, beach);
            seaRepo.save(entity);
            saved.incrementAndGet();
        }
        metrics.recordIngestionBeach(SourceType.SEA_FORECAST, "ok");
    }

    private void handleAdvisory(BeachEntity beach,
                                SourceRequest req,
                                AtomicInteger fetched,
                                AtomicInteger saved,
                                AtomicInteger failures,
                                AtomicInteger staleHttp) {
        FetchResult<HealthAdvisoryRecord> result = advisoryAdapter.fetch(req);
        if (!result.isSuccess()) {
            failures.incrementAndGet();
            metrics.recordIngestionBeach(SourceType.HEALTH_ADVISORY, "failed");
            return;
        }
        if (containsStale(result)) {
            staleHttp.incrementAndGet();
            metrics.recordIngestionStaleFallback(SourceType.HEALTH_ADVISORY);
        }
        fetched.addAndGet(result.getRecords().size());
        for (HealthAdvisoryRecord r : result.getRecords()) {
            HealthAdvisorySnapshotEntity entity = mapAdvisoryRecord(r, beach);
            advisoryRepo.save(entity);
            saved.incrementAndGet();
        }
        metrics.recordIngestionBeach(SourceType.HEALTH_ADVISORY, "ok");
    }

    private void handleJellyfish(BeachEntity beach,
                                 SourceRequest req,
                                 AtomicInteger fetched,
                                 AtomicInteger saved,
                                 AtomicInteger failures,
                                 AtomicInteger staleHttp) {
        FetchResult<JellyfishRecord> result = jellyfishAdapter.fetch(req);
        if (!result.isSuccess()) {
            failures.incrementAndGet();
            metrics.recordIngestionBeach(SourceType.JELLYFISH, "failed");
            return;
        }
        if (containsStale(result)) {
            staleHttp.incrementAndGet();
            metrics.recordIngestionStaleFallback(SourceType.JELLYFISH);
        }
        fetched.addAndGet(result.getRecords().size());
        for (JellyfishRecord r : result.getRecords()) {
            JellyfishReportAggregateEntity entity = mapJellyfishRecord(r, beach);
            jellyfishRepo.save(entity);
            saved.incrementAndGet();
        }
        metrics.recordIngestionBeach(SourceType.JELLYFISH, "ok");
    }

    private static boolean containsStale(FetchResult<?> result) {
        return result.getWarnings() != null && result.getWarnings().contains(STALE_HTTP);
    }

    private static <T> List<List<T>> partition(List<T> items, int batchSize) {
        List<List<T>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i += batchSize) {
            out.add(items.subList(i, Math.min(items.size(), i + batchSize)));
        }
        return out;
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
