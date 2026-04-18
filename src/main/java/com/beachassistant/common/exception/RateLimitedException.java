package com.beachassistant.common.exception;

import java.time.Duration;

public class RateLimitedException extends BeachAssistantException implements UserFacing {

    private final Duration retryAfter;

    public RateLimitedException(Duration retryAfter) {
        super("RATE_LIMITED", "Too many requests; please retry later");
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
