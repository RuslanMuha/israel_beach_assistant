package com.beachassistant.domain.model;

import com.beachassistant.common.enums.Confidence;
import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.ReasonCode;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.common.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Builder
public class BeachDecision {

    private final String beachSlug;
    private final String beachDisplayName;
    private final String city;

    private final Recommendation recommendation;
    private final Confidence confidence;
    private final List<ReasonCode> reasonCodes;
    private final String humanSummary;

    /** Mirrors {@link com.beachassistant.domain.model.BeachSignals#lifeguardScheduleKnown}. */
    @Builder.Default
    private final boolean lifeguardScheduleKnown = true;
    /** True when lifeguards are on duty within scheduled hours (meaningful only if schedule is known). */
    @Builder.Default
    private final boolean lifeguardOnDuty = false;

    private final Double waveHeightM;
    private final Double airTemperatureC;
    private final Double relativeHumidityPct;
    private final Double uvIndex;
    private final Double windSpeedMps;
    /** 8-point compass for wind (e.g. NW). */
    private final String windDirection;
    private final Double seaTemperatureC;

    private final FreshnessStatus freshnessStatus;
    private final ZonedDateTime generatedAt;
    private final ZonedDateTime effectiveFrom;
    private final ZonedDateTime effectiveTo;
    private final boolean intervalIsInferred;

    private final Map<SourceType, FreshnessStatus> sourceFreshness;
    private final List<SourceType> missingSourceTypes;

    /** Last capture time per source (transparency). */
    @Builder.Default
    private final Map<SourceType, ZonedDateTime> sourceCapturedAt = Map.of();
}
