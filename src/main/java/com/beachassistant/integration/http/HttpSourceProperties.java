package com.beachassistant.integration.http;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

import java.time.Duration;

/**
 * Transport and pacing settings for one outbound provider. GET requests are idempotent; retries are bounded.
 */
@Getter
@Setter
public class HttpSourceProperties {

    private boolean enabled = true;

    private Duration connectTimeout = Duration.ofSeconds(5);

    private Duration readTimeout = Duration.ofSeconds(20);

    /**
     * Additional attempts after the first try for retryable conditions (429, transient 5xx, timeouts).
     */
    @Min(0)
    @Max(10)
    private int maxRetries = 3;

    private Duration initialBackoff = Duration.ofMillis(250);

    private Duration maxBackoff = Duration.ofSeconds(45);

    @Min(1)
    @Max(10_000)
    private double backoffMultiplier = 2.0;

    /**
     * If a response was fetched recently, skip the network call and reuse the cached body (reduces bursts).
     * Zero disables short-circuiting (always attempts a live fetch first when cache exists for fallback only).
     */
    private Duration shortCircuitTtl = Duration.ofMinutes(10);

    /**
     * Maximum age of a cached body to return when live fetch fails (stale-while-error).
     * Zero disables stale fallback.
     */
    private Duration staleFallbackMaxAge = Duration.ofHours(6);

    /**
     * Minimum spacing between outbound calls for this source (pacing / anti-burst).
     */
    @Min(0)
    @Max(600_000)
    private long pacingMillisBetweenRequests = 0L;

    /**
     * When false, live calls are not gated by Resilience4j (testing or emergency override).
     */
    private boolean circuitBreakerEnabled = true;
}
