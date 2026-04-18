package com.beachassistant.source.advisory;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

/**
 * Bathing-water quality advisory (E. coli / enterococci / sewage-event flag). Distinct from the
 * air-quality path because the source feeds are fundamentally different (Ministry of Health
 * laboratory results / municipal announcements).
 */
@Value
@Builder
public class WaterQualityAdvisoryRecord {
    String beachSlug;
    boolean active;
    /** One of: BATHING_SAFE, ADVISORY, CLOSED_SEWAGE_EVENT, UNKNOWN. */
    String advisoryLevel;
    String message;
    ZonedDateTime validFrom;
    ZonedDateTime validTo;
    ZonedDateTime capturedAt;
    String rawPayloadJson;
}
