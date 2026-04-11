package com.beachassistant.source.jellyfish;

import com.beachassistant.common.enums.JellyfishSeverity;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class JellyfishRecord {

    private final String beachSlug;
    private final ZonedDateTime capturedAt;
    private final ZonedDateTime windowStart;
    private final ZonedDateTime windowEnd;
    private final JellyfishSeverity severityLevel;
    private final Integer reportCount;
    private final String confidenceLevel;
    private final String rawPayloadJson;
}
