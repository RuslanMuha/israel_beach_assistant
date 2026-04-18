package com.beachassistant.source.closure;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

/**
 * Record emitted by {@link BeachClosureAdapter}. Consumers persist one row in
 * {@code closure_snapshot} per emission.
 */
@Value
@Builder
public class BeachClosureRecord {
    String beachSlug;
    boolean closed;
    String reason;
    String source;
    ZonedDateTime effectiveFrom;
    ZonedDateTime effectiveUntil;
    String rawPayloadJson;
    ZonedDateTime capturedAt;
}
