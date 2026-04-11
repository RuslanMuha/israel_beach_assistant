package com.beachassistant.source.sea;

import com.beachassistant.common.enums.SeaRiskLevel;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class SeaForecastRecord {

    private final String beachSlug;
    private final ZonedDateTime capturedAt;
    private final ZonedDateTime validFrom;
    private final ZonedDateTime validTo;
    private final SeaRiskLevel seaRiskLevel;
    private final Double waveHeightM;
    private final Double windSpeedMps;
    private final String windDirection;
    private final Double airTemperatureC;
    private final Double relativeHumidityPct;
    private final Double uvIndex;
    private final Double seaTemperatureC;
    private final boolean intervalIsInferred;
    private final String rawPayloadJson;
}
