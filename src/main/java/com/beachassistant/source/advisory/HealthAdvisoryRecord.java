package com.beachassistant.source.advisory;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class HealthAdvisoryRecord {

    private final String beachSlug;
    private final ZonedDateTime capturedAt;
    private final ZonedDateTime validFrom;
    private final ZonedDateTime validTo;
    private final boolean active;
    private final String advisoryType;
    private final String message;
    private final String rawPayloadJson;
}
