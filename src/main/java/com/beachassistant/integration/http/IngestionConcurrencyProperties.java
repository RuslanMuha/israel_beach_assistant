package com.beachassistant.integration.http;

import com.beachassistant.common.enums.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * Limits ingestion fan-out across beaches (batching, pacing, concurrency).
 */
@Getter
@Setter
public class IngestionConcurrencyProperties {

    @Min(1)
    @Max(10_000)
    private int batchSize = 25;

    /**
     * Maximum beaches processed in parallel within one ingestion cycle.
     */
    @Min(1)
    @Max(64)
    private int maxConcurrentBeaches = 2;

    /**
     * Pause after each batch of beaches completes (parallel sea/jellyfish) and between advisory provider groups.
     */
    @Min(0)
    @Max(600_000)
    private long pacingMillisBetweenBeaches = 75L;

    /**
     * Minimum age required before refreshing a source for a beach. Zero means "always refresh".
     */
    private Map<SourceType, Duration> refreshWindowBySource = defaultRefreshWindows();

    public Duration refreshWindowFor(SourceType sourceType) {
        return refreshWindowBySource.getOrDefault(sourceType, Duration.ZERO);
    }

    private static Map<SourceType, Duration> defaultRefreshWindows() {
        Map<SourceType, Duration> windows = new EnumMap<>(SourceType.class);
        windows.put(SourceType.SEA_FORECAST, Duration.ofMinutes(45));
        windows.put(SourceType.HEALTH_ADVISORY, Duration.ofMinutes(90));
        windows.put(SourceType.JELLYFISH, Duration.ofHours(2));
        return windows;
    }
}
