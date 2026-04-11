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
import com.beachassistant.scheduler.IngestionCycleGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.CacheManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final IngestionCycleGuard cycleGuard;
    private final AsyncTaskExecutor ingestionCoordinatorExecutor;

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
                             @Qualifier("beachIngestionExecutor") AsyncTaskExecutor beachIngestionExecutor,
                             IngestionCycleGuard cycleGuard,
                             @Qualifier("ingestionCoordinatorExecutor") AsyncTaskExecutor ingestionCoordinatorExecutor) {
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
        this.cycleGuard = cycleGuard;
        this.ingestionCoordinatorExecutor = ingestionCoordinatorExecutor;
    }

    /**
     * Synchronous ingestion (scheduler). Overlap for the same {@link SourceType} is handled by {@link com.beachassistant.scheduler.IngestionScheduler}.
     */
    public IngestionRunEntity ingest(SourceType sourceType) {
        IngestionRunEntity run = newRunningRun(sourceType);
        ingestionRunRepo.save(run);
        return runIngestionCycle(run, sourceType);
    }

    /**
     * Admin-triggered ingestion: returns after persisting a RUNNING row and scheduling work on {@link #ingestionCoordinatorExecutor}.
     * Uses {@link IngestionCycleGuard} like scheduled runs. Empty when another cycle for this source is already active.
     */
    public Optional<IngestionRunEntity> startIngestionAsync(SourceType sourceType) {
        if (!cycleGuard.tryBegin(sourceType)) {
            return Optional.empty();
        }
        IngestionRunEntity run = newRunningRun(sourceType);
        run = ingestionRunRepo.save(run);
        final Long runId = run.getId();
        ingestionCoordinatorExecutor.execute(() -> {
            try {
                IngestionRunEntity managed = ingestionRunRepo.findById(runId)
                        .orElseThrow(() -> new IllegalStateException("ingestion run missing: " + runId));
                runIngestionCycle(managed, sourceType);
            } finally {
                cycleGuard.end(sourceType);
            }
        });
        return Optional.of(run);
    }

    private IngestionRunEntity newRunningRun(SourceType sourceType) {
        IngestionRunEntity run = new IngestionRunEntity();
        run.setSourceType(sourceType);
        run.setStartedAt(ZonedDateTime.now());
        run.setStatus("RUNNING");
        return run;
    }

    private IngestionRunEntity runIngestionCycle(IngestionRunEntity run, SourceType sourceType) {
        long t0 = System.nanoTime();
        AtomicInteger fetched = new AtomicInteger();
        AtomicInteger saved = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        AtomicInteger staleHttp = new AtomicInteger();
        AtomicInteger skippedFresh = new AtomicInteger();
        AtomicInteger reusedLastSnapshot = new AtomicInteger();
        AtomicInteger groupedFetches = new AtomicInteger();

        int beachesTotal = 0;
        try {
            List<BeachEntity> beaches = beachRepository.findAllByActiveTrue();
            beachesTotal = beaches.size();
            List<String> beachSlugs = beaches.stream().map(BeachEntity::getSlug).toList();
            int batchSize = Math.max(1, integrationProperties.getIngestion().getBatchSize());

            if (sourceType == SourceType.HEALTH_ADVISORY) {
                ingestAdvisoryGrouped(
                        beaches, fetched, saved, failures, staleHttp, skippedFresh, reusedLastSnapshot, groupedFetches
                );
            } else {
                List<List<BeachEntity>> batches = partition(beaches, batchSize);
                for (int bi = 0; bi < batches.size(); bi++) {
                    List<BeachEntity> batch = batches.get(bi);
                    List<CompletableFuture<Void>> futures = new ArrayList<>();
                    for (BeachEntity beach : batch) {
                        futures.add(CompletableFuture.runAsync(() ->
                                        transactionTemplate.executeWithoutResult(status ->
                                                ingestSingleBeach(beach, sourceType, fetched, saved, failures, staleHttp, skippedFresh, reusedLastSnapshot)),
                                beachIngestionExecutor));
                    }
                    CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
                    if (bi < batches.size() - 1) {
                        sleepPacingBetweenBatches();
                    }
                }
            }

            evictBeachStatusForSlugs(beachSlugs);
            int failCount = failures.get();
            if (failCount == 0) {
                run.setStatus("SUCCESS");
            } else if (saved.get() > 0 || fetched.get() > 0) {
                run.setStatus("PARTIAL");
            } else {
                run.setStatus("FAILED");
            }
            metrics.recordSourceFetch(sourceType, failCount == 0);
            String summary = "ingestion_cycle source=%s beaches=%d saved_records=%d fetched=%d failures=%d stale_http_fallback=%d skipped_fresh=%d reused_last_snapshot=%d grouped_fetches=%d duration_ms=%d"
                    .formatted(sourceType, beachesTotal, saved.get(), fetched.get(), failCount, staleHttp.get(),
                            skippedFresh.get(), reusedLastSnapshot.get(), groupedFetches.get(),
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

    private void sleepPacingBetweenBatches() {
        long ms = integrationProperties.getIngestion().getPacingMillisBetweenBeaches();
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void ingestSingleBeach(BeachEntity beach,
                                   SourceType sourceType,
                                   AtomicInteger fetched,
                                   AtomicInteger saved,
                                   AtomicInteger failures,
                                   AtomicInteger staleHttp,
                                   AtomicInteger skippedFresh,
                                   AtomicInteger reusedLastSnapshot) {
        SourceRequest req = SourceRequest.builder()
                .beachSlug(beach.getSlug())
                .build();
        try {
            switch (sourceType) {
                case SEA_FORECAST -> handleSea(beach, req, fetched, saved, failures, staleHttp, skippedFresh, reusedLastSnapshot);
                case HEALTH_ADVISORY -> handleAdvisory(beach, req, fetched, saved, failures, staleHttp, skippedFresh, reusedLastSnapshot);
                case JELLYFISH -> handleJellyfish(beach, req, fetched, saved, failures, staleHttp, skippedFresh, reusedLastSnapshot);
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
                           AtomicInteger staleHttp,
                           AtomicInteger skippedFresh,
                           AtomicInteger reusedLastSnapshot) {
        if (!shouldRefreshSea(beach)) {
            skippedFresh.incrementAndGet();
            metrics.recordIngestionSkippedFresh(SourceType.SEA_FORECAST);
            metrics.recordIngestionBeach(SourceType.SEA_FORECAST, "skipped_fresh");
            return;
        }
        FetchResult<SeaForecastRecord> result = seaAdapter.fetch(req);
        if (!result.isSuccess()) {
            markFailureOrReuse(SourceType.SEA_FORECAST, failures, reusedLastSnapshot, hasSeaSnapshot(beach.getId()));
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
                                AtomicInteger staleHttp,
                                AtomicInteger skippedFresh,
                                AtomicInteger reusedLastSnapshot) {
        if (!shouldRefreshAdvisory(beach)) {
            skippedFresh.incrementAndGet();
            metrics.recordIngestionSkippedFresh(SourceType.HEALTH_ADVISORY);
            metrics.recordIngestionBeach(SourceType.HEALTH_ADVISORY, "skipped_fresh");
            return;
        }
        FetchResult<HealthAdvisoryRecord> result = advisoryAdapter.fetch(req);
        if (!result.isSuccess()) {
            markFailureOrReuse(SourceType.HEALTH_ADVISORY, failures, reusedLastSnapshot, hasAdvisorySnapshot(beach.getId()));
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
                                 AtomicInteger staleHttp,
                                 AtomicInteger skippedFresh,
                                 AtomicInteger reusedLastSnapshot) {
        if (!shouldRefreshJellyfish(beach)) {
            skippedFresh.incrementAndGet();
            metrics.recordIngestionSkippedFresh(SourceType.JELLYFISH);
            metrics.recordIngestionBeach(SourceType.JELLYFISH, "skipped_fresh");
            return;
        }
        FetchResult<JellyfishRecord> result = jellyfishAdapter.fetch(req);
        if (!result.isSuccess()) {
            markFailureOrReuse(SourceType.JELLYFISH, failures, reusedLastSnapshot, hasJellyfishSnapshot(beach.getId()));
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

    private void ingestAdvisoryGrouped(List<BeachEntity> beaches,
                                       AtomicInteger fetched,
                                       AtomicInteger saved,
                                       AtomicInteger failures,
                                       AtomicInteger staleHttp,
                                       AtomicInteger skippedFresh,
                                       AtomicInteger reusedLastSnapshot,
                                       AtomicInteger groupedFetches) {
        Map<String, List<BeachEntity>> groups = new LinkedHashMap<>();
        for (BeachEntity beach : beaches) {
            if (!shouldRefreshAdvisory(beach)) {
                skippedFresh.incrementAndGet();
                metrics.recordIngestionSkippedFresh(SourceType.HEALTH_ADVISORY);
                metrics.recordIngestionBeach(SourceType.HEALTH_ADVISORY, "skipped_fresh");
                continue;
            }
            String groupKey = advisoryAdapter.providerLocationKeyForBeach(beach);
            groups.computeIfAbsent(groupKey, ignored -> new ArrayList<>()).add(beach);
        }

        List<List<BeachEntity>> groupList = new ArrayList<>(groups.values());
        for (int gi = 0; gi < groupList.size(); gi++) {
            List<BeachEntity> groupBeaches = groupList.get(gi);
            BeachEntity seed = groupBeaches.getFirst();
            groupedFetches.incrementAndGet();
            metrics.recordIngestionGroupedFetch(SourceType.HEALTH_ADVISORY);
            SourceRequest req = SourceRequest.builder().beachSlug(seed.getSlug()).build();
            FetchResult<HealthAdvisoryRecord> result = advisoryAdapter.fetch(req);
            if (!result.isSuccess()) {
                for (BeachEntity beach : groupBeaches) {
                    markFailureOrReuse(
                            SourceType.HEALTH_ADVISORY,
                            failures,
                            reusedLastSnapshot,
                            hasAdvisorySnapshot(beach.getId())
                    );
                }
                if (gi < groupList.size() - 1) {
                    sleepPacingBetweenBatches();
                }
                continue;
            }
            if (containsStale(result)) {
                staleHttp.incrementAndGet();
                metrics.recordIngestionStaleFallback(SourceType.HEALTH_ADVISORY);
            }
            fetched.addAndGet(result.getRecords().size());
            for (BeachEntity beach : groupBeaches) {
                for (HealthAdvisoryRecord r : result.getRecords()) {
                    HealthAdvisorySnapshotEntity entity = mapAdvisoryRecord(r, beach);
                    advisoryRepo.save(entity);
                    saved.incrementAndGet();
                }
                metrics.recordIngestionBeach(SourceType.HEALTH_ADVISORY, "ok");
            }
            if (gi < groupList.size() - 1) {
                sleepPacingBetweenBatches();
            }
        }
    }

    private void markFailureOrReuse(SourceType sourceType,
                                    AtomicInteger failures,
                                    AtomicInteger reusedLastSnapshot,
                                    boolean hasSnapshot) {
        if (hasSnapshot) {
            reusedLastSnapshot.incrementAndGet();
            metrics.recordIngestionReusedLastSnapshot(sourceType);
            metrics.recordIngestionBeach(sourceType, "reused_last_snapshot");
            return;
        }
        failures.incrementAndGet();
        metrics.recordIngestionBeach(sourceType, "failed");
    }

    private boolean shouldRefreshSea(BeachEntity beach) {
        return shouldRefresh(seaRepo.findTopByBeachIdOrderByCapturedAtDesc(beach.getId())
                .map(SeaConditionSnapshotEntity::getCapturedAt)
                .orElse(null), SourceType.SEA_FORECAST);
    }

    private boolean shouldRefreshAdvisory(BeachEntity beach) {
        return shouldRefresh(advisoryRepo.findTopByBeachIdOrderByCapturedAtDesc(beach.getId())
                .map(HealthAdvisorySnapshotEntity::getCapturedAt)
                .orElse(null), SourceType.HEALTH_ADVISORY);
    }

    private boolean shouldRefreshJellyfish(BeachEntity beach) {
        return shouldRefresh(jellyfishRepo.findTopByBeachIdOrderByCapturedAtDesc(beach.getId())
                .map(JellyfishReportAggregateEntity::getCapturedAt)
                .orElse(null), SourceType.JELLYFISH);
    }

    private boolean shouldRefresh(ZonedDateTime capturedAt, SourceType sourceType) {
        Duration window = integrationProperties.getIngestion().refreshWindowFor(sourceType);
        if (window.isZero() || window.isNegative()) {
            return true;
        }
        if (capturedAt == null) {
            return true;
        }
        return Duration.between(capturedAt.toInstant(), Instant.now()).compareTo(window) >= 0;
    }

    private boolean hasSeaSnapshot(Long beachId) {
        return seaRepo.findTopByBeachIdOrderByCapturedAtDesc(beachId).isPresent();
    }

    private boolean hasAdvisorySnapshot(Long beachId) {
        return advisoryRepo.findTopByBeachIdOrderByCapturedAtDesc(beachId).isPresent();
    }

    private boolean hasJellyfishSnapshot(Long beachId) {
        return jellyfishRepo.findTopByBeachIdOrderByCapturedAtDesc(beachId).isPresent();
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

    private void evictBeachStatusForSlugs(List<String> slugs) {
        var statusCache = cacheManager.getCache("beachStatus");
        if (statusCache == null) {
            return;
        }
        for (String slug : slugs) {
            statusCache.evict(slug);
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
