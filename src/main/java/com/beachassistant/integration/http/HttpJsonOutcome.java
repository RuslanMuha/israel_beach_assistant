package com.beachassistant.integration.http;

import org.springframework.lang.Nullable;

/**
 * Result of a resilient GET returning a JSON body string.
 */
public record HttpJsonOutcome(
        @Nullable String body,
        boolean shortCircuit,
        boolean staleFallback,
        int attempts,
        int lastHttpStatus,
        @Nullable String errorMessage
) {
    public boolean success() {
        return body != null;
    }
}
