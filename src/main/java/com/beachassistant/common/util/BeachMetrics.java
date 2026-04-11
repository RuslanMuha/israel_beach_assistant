package com.beachassistant.common.util;

import com.beachassistant.common.enums.Confidence;
import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.common.enums.SourceType;
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
}
