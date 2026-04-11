package com.beachassistant.integration.http;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/**
 * Non-overlap and startup spreading for scheduled ingestion.
 */
@Getter
@Setter
public class SchedulerSafetyProperties {

    /**
     * When true, a scheduled tick is skipped if the previous run for the same flow has not finished.
     */
    private boolean skipIfOverlap = true;

    /**
     * Delay before the first scheduled run after startup (reduces cold-start stampedes with the web tier).
     */
    @Min(0)
    @Max(3_600_000)
    private long initialDelayMs = 90_000L;
}
