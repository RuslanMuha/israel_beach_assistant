package com.beachassistant.web.dto;

import com.beachassistant.common.enums.FreshnessStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class LifeguardHoursDto {

    private final String beach;
    private final boolean onDuty;
    private final String openTime;
    private final String closeTime;
    private final String scheduleType;
    private final FreshnessStatus freshnessStatus;
    private final ZonedDateTime capturedAt;
}
