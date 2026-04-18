package com.beachassistant.web.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code beach.api.rate-limit.*} — Bucket4j-backed rate limit for public REST endpoints under
 * {@code /api/v1/beaches/**}. Disabled by default so tests and local development stay fast; flip
 * {@link #isEnabled()} on per environment.
 */
@Component
@ConfigurationProperties(prefix = "beach.api.rate-limit")
@Getter
@Setter
public class ApiRateLimitProperties {
    private boolean enabled = false;
    private int requestsPerMinute = 120;
    private int burst = 30;
    private Duration bucketIdleTtl = Duration.ofMinutes(30);
    private int maxTrackedClients = 10_000;
}
