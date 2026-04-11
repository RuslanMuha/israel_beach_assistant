package com.beachassistant.domain.model;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.JellyfishSeverity;
import com.beachassistant.common.enums.SeaRiskLevel;
import com.beachassistant.common.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;

/**
 * Aggregated signals for a single beach at a point in time, ready for the decision engine.
 */
@Getter
@Builder
public class BeachSignals {

    private final String beachSlug;
    private final String beachDisplayName;
    private final String city;

    // Sea conditions
    private final SeaRiskLevel seaRiskLevel;
    private final Double waveHeightM;
    private final Double airTemperatureC;
    private final Double relativeHumidityPct;
    private final Double uvIndex;
    private final Double windSpeedMps;
    /** 8-point compass, e.g. N, NE (from Open-Meteo). */
    private final String windDirection;
    private final Double seaTemperatureC;
    private final ZonedDateTime seaCapturedAt;
    private final ZonedDateTime seaValidFrom;
    private final ZonedDateTime seaValidTo;
    private final boolean seaIntervalIsInferred;

    // Health advisory
    private final boolean healthAdvisoryActive;
    private final String healthAdvisoryMessage;
    private final ZonedDateTime advisoryCapturedAt;

    // Lifeguard
    /** True when an active schedule row exists for today (open/close evaluated). */
    @Builder.Default
    private final boolean lifeguardScheduleKnown = false;
    private final boolean lifeguardOnDuty;
    private final String lifeguardOpenTime;
    private final String lifeguardCloseTime;
    private final ZonedDateTime lifeguardCapturedAt;

    // Jellyfish
    private final JellyfishSeverity jellyfishSeverity;
    private final Integer jellyfishReportCount;
    private final ZonedDateTime jellyfishWindowStart;
    private final ZonedDateTime jellyfishWindowEnd;
    private final ZonedDateTime jellyfishCapturedAt;

    // Beach closed flag
    private final boolean beachClosed;

    // Per-source fetch failures
    @Builder.Default
    private final Map<SourceType, String> fetchFailures = new EnumMap<>(SourceType.class);

    // Per-source freshness (computed before engine)
    @Builder.Default
    private final Map<SourceType, FreshnessStatus> sourceFreshness = new EnumMap<>(SourceType.class);

    /** When each source's snapshot was captured (for transparency). */
    @Builder.Default
    private final Map<SourceType, ZonedDateTime> sourceCapturedAt = new EnumMap<>(SourceType.class);
}
