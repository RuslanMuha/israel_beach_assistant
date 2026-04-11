package com.beachassistant.app.usecase;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.integration.http.BeachIntegrationProperties;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.HealthAdvisorySnapshotEntity;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import com.beachassistant.persistence.entity.SeaConditionSnapshotEntity;
import com.beachassistant.persistence.repository.*;
import com.beachassistant.source.advisory.HealthAdvisoryAdapter;
import com.beachassistant.source.advisory.HealthAdvisoryRecord;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceRequest;
import com.beachassistant.source.jellyfish.JellyfishAdapter;
import com.beachassistant.scheduler.IngestionCycleGuard;
import com.beachassistant.source.sea.SeaForecastAdapter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class IngestionUseCaseTest {

    @Mock
    private BeachRepository beachRepository;
    @Mock
    private SeaConditionSnapshotRepository seaRepo;
    @Mock
    private HealthAdvisorySnapshotRepository advisoryRepo;
    @Mock
    private JellyfishReportAggregateRepository jellyfishRepo;
    @Mock
    private IngestionRunRepository ingestionRunRepo;
    @Mock
    private SeaForecastAdapter seaAdapter;
    @Mock
    private HealthAdvisoryAdapter advisoryAdapter;
    @Mock
    private JellyfishAdapter jellyfishAdapter;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private IngestionCycleGuard cycleGuard;

    private SimpleMeterRegistry meterRegistry;
    private BeachMetrics metrics;
    private BeachIntegrationProperties integrationProperties;
    private IngestionUseCase useCase;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metrics = new BeachMetrics(meterRegistry);
        integrationProperties = new BeachIntegrationProperties();
        integrationProperties.getIngestion().setBatchSize(10);
        integrationProperties.getIngestion().setRefreshWindowBySource(Map.of(
                SourceType.SEA_FORECAST, Duration.ofMinutes(45),
                SourceType.HEALTH_ADVISORY, Duration.ZERO
        ));

        AsyncTaskExecutor directExecutor = new TaskExecutorAdapter(Runnable::run);
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager("beachStatus");

        TransactionStatus status = new SimpleTransactionStatus();
        lenient().when(transactionManager.getTransaction(any())).thenReturn(status);
        lenient().doNothing().when(transactionManager).commit(any());
        lenient().doNothing().when(transactionManager).rollback(any());
        when(ingestionRunRepo.save(any(IngestionRunEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(cycleGuard.tryBegin(any())).thenReturn(true);

        useCase = new IngestionUseCase(
                beachRepository,
                seaRepo,
                advisoryRepo,
                jellyfishRepo,
                ingestionRunRepo,
                seaAdapter,
                advisoryAdapter,
                jellyfishAdapter,
                cacheManager,
                metrics,
                transactionManager,
                integrationProperties,
                directExecutor,
                cycleGuard,
                directExecutor
        );
    }

    @Test
    void seaIngestion_skipsFetchWhenSnapshotStillFresh() {
        BeachEntity beach = beach(1L, "b1");
        SeaConditionSnapshotEntity snapshot = new SeaConditionSnapshotEntity();
        snapshot.setCapturedAt(ZonedDateTime.now().minusMinutes(5));

        when(beachRepository.findAllByActiveTrue()).thenReturn(List.of(beach));
        when(seaRepo.findTopByBeachIdOrderByCapturedAtDesc(1L)).thenReturn(java.util.Optional.of(snapshot));

        IngestionRunEntity run = useCase.ingest(SourceType.SEA_FORECAST);

        verify(seaAdapter, never()).fetch(any(SourceRequest.class));
        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(run.getRecordsFetched()).isZero();
        assertThat(meterRegistry.get("ingestion_skipped_fresh_total")
                .tag("source", SourceType.SEA_FORECAST.name())
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void advisoryIngestion_groupsBeachesByProviderLocation() {
        BeachEntity beach1 = beach(1L, "b1");
        BeachEntity beach2 = beach(2L, "b2");
        when(beachRepository.findAllByActiveTrue()).thenReturn(List.of(beach1, beach2));
        when(advisoryRepo.findTopByBeachIdOrderByCapturedAtDesc(anyLong())).thenReturn(java.util.Optional.empty());
        when(advisoryAdapter.providerLocationKeyForBeach(beach1)).thenReturn("groupA");
        when(advisoryAdapter.providerLocationKeyForBeach(beach2)).thenReturn("groupA");

        HealthAdvisoryRecord record = HealthAdvisoryRecord.builder()
                .beachSlug("b1")
                .capturedAt(ZonedDateTime.now())
                .validFrom(ZonedDateTime.now())
                .validTo(ZonedDateTime.now().plusHours(1))
                .active(false)
                .advisoryType("NONE")
                .message(null)
                .rawPayloadJson("{}")
                .build();
        when(advisoryAdapter.fetch(any(SourceRequest.class)))
                .thenReturn(FetchResult.success(SourceType.HEALTH_ADVISORY, List.of(record)));

        IngestionRunEntity run = useCase.ingest(SourceType.HEALTH_ADVISORY);

        verify(advisoryAdapter, times(1)).fetch(any(SourceRequest.class));
        verify(advisoryRepo, times(2)).save(any(HealthAdvisorySnapshotEntity.class));
        assertThat(run.getStatus()).isEqualTo("SUCCESS");
        assertThat(meterRegistry.get("ingestion_grouped_fetch_total")
                .tag("source", SourceType.HEALTH_ADVISORY.name())
                .counter().count()).isEqualTo(1.0);
    }

    @Test
    void startIngestionAsync_returnsEmptyWhenOverlap() {
        when(cycleGuard.tryBegin(SourceType.SEA_FORECAST)).thenReturn(false);

        Optional<IngestionRunEntity> out = useCase.startIngestionAsync(SourceType.SEA_FORECAST);

        assertThat(out).isEmpty();
        verify(ingestionRunRepo, never()).save(any());
    }

    private BeachEntity beach(Long id, String slug) {
        BeachEntity beach = new BeachEntity();
        beach.setId(id);
        beach.setSlug(slug);
        beach.setLatitude(31.8);
        beach.setLongitude(34.6);
        beach.setActive(true);
        return beach;
    }
}
