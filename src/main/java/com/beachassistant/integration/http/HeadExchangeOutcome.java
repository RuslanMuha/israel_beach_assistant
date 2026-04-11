package com.beachassistant.integration.http;

import org.springframework.lang.Nullable;

/**
 * Result of a resilient HEAD (or bodiless) check.
 */
public record HeadExchangeOutcome(
        int statusCode,
        boolean reachable,
        int attempts,
        @Nullable String errorMessage
) {
}
