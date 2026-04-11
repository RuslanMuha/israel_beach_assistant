package com.beachassistant.common.util;

import com.beachassistant.common.enums.Confidence;
import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.integration.IntegrationSourceKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class BeachMetrics {

    private final MeterRegistry registry;

    public BeachMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSourceFetch(SourceType sourceType, boolean success) {
        Counter.builder("source_fetch_total")
                .tag("source", sourceType.name())
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    public void recordSourceFetchDuration(SourceType sourceType, long millis) {
        Timer.builder("source_fetch_duration_ms")
                .tag("source", sourceType.name())
                .register(registry)
                .record(Duration.ofMillis(millis));
    }

    public void recordDecision(Recommendation recommendation) {
        Counter.builder("decision_generation_total")
                .tag("result", recommendation.name())
                .register(registry)
                .increment();
    }

    public void recordDecisionConfidence(Confidence confidence) {
        Counter.builder("decision_confidence_total")
                .tag("level", confidence.name())
                .register(registry)
                .increment();
    }

    public void recordBotRequest(String command, String result) {
        Counter.builder("bot_request_total")
                .tag("command", command)
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public void recordFreshnessStatus(SourceType sourceType, FreshnessStatus status) {
        Counter.builder("freshness_status_total")
                .tag("source", sourceType.name())
                .tag("status", status.name())
                .register(registry)
                .increment();
    }

    public void recordCameraSnapshot(boolean success) {
        Counter.builder("camera_snapshot_total")
                .tag("result", success ? "success" : "failure")
                .register(registry)
                .increment();
    }

    /**
     * Outbound integration attempts (bounded {@code op} values per adapter).
     */
    public void recordIntegrationHttp(IntegrationSourceKey sourceKey, String operation, String outcome) {
        Counter.builder("integration_http_total")
                .tag("source", sourceKey.name())
                .tag("op", operation)
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordIntegrationRetry(IntegrationSourceKey sourceKey) {
        Counter.builder("integration_retry_total")
                .tag("source", sourceKey.name())
                .register(registry)
                .increment();
    }

    public void recordIntegrationRateLimit(IntegrationSourceKey sourceKey) {
        Counter.builder("integration_rate_limit_total")
                .tag("source", sourceKey.name())
                .register(registry)
                .increment();
    }

    public void recordIntegrationCacheHit(IntegrationSourceKey sourceKey, String kind) {
        Counter.builder("integration_cache_total")
                .tag("source", sourceKey.name())
                .tag("kind", kind)
                .register(registry)
                .increment();
    }

    public void recordIntegrationLatency(IntegrationSourceKey sourceKey, long millis) {
        Timer.builder("integration_http_latency_ms")
                .tag("source", sourceKey.name())
                .register(registry)
                .record(Duration.ofMillis(millis));
    }

    public void recordCircuitBreakerNotPermitted(IntegrationSourceKey sourceKey) {
        Counter.builder("integration_circuit_breaker_not_permitted_total")
                .tag("source", sourceKey.name())
                .register(registry)
                .increment();
    }

    public void recordIngestionBeach(SourceType sourceType, String outcome) {
        Counter.builder("ingestion_beach_total")
                .tag("source", sourceType.name())
                .tag("outcome", outcome)
                .register(registry)
                .increment();
    }

    public void recordIngestionStaleFallback(SourceType sourceType) {
        Counter.builder("ingestion_stale_fallback_total")
                .tag("source", sourceType.name())
                .register(registry)
                .increment();
    }

    public void recordIngestionCycleSkippedOverlap(SourceType sourceType) {
        Counter.builder("ingestion_cycle_skipped_overlap_total")
                .tag("source", sourceType.name())
                .register(registry)
                .increment();
    }
}
