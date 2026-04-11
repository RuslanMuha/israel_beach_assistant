package com.beachassistant.integration.http;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

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
     * Pause between finishing one beach and starting the next when concurrency is 1; still applies between batches.
     */
    @Min(0)
    @Max(600_000)
    private long pacingMillisBetweenBeaches = 75L;
}
