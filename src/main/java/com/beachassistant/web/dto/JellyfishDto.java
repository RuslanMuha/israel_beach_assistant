package com.beachassistant.web.dto;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.JellyfishSeverity;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class JellyfishDto {

    private final String beach;
    private final JellyfishSeverity severityLevel;
    private final String confidenceLevel;
    private final Integer reportCount;
    private final ZonedDateTime windowStart;
    private final ZonedDateTime windowEnd;
    private final FreshnessStatus freshnessStatus;
    private final ZonedDateTime capturedAt;
}
