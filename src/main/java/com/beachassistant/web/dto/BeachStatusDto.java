package com.beachassistant.web.dto;

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
public class BeachStatusDto {

    private final String beach;
    private final String city;
    private final Recommendation recommendation;
    private final Confidence confidence;
    private final List<ReasonCode> reasons;
    private final String summary;
    private final FreshnessStatus freshnessStatus;
    private final ZonedDateTime updatedAt;
    private final ZonedDateTime validFrom;
    private final ZonedDateTime validTo;
    private final Map<SourceType, FreshnessStatus> sources;
    private final Map<SourceType, ZonedDateTime> sourceCapturedAt;
    private final List<SourceType> missingSources;
    private final Double seaTemperatureC;
    private final String windDirection;
    private final Double windSpeedMps;
}
