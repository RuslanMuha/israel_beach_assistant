package com.beachassistant.source.closure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * {@code beach.providers.closure.*} — scraping targets per beach slug.
 *
 * <p>Shipped off by default; enable via {@code beach.providers.closure.enabled=true}.
 * Each entry maps a beach slug to a scrape URL and optional CSS selector.</p>
 */
@Component
@ConfigurationProperties(prefix = "beach.providers.closure")
@Getter
@Setter
public class BeachClosureProperties {
    private boolean enabled = false;
    private Duration cadence = Duration.ofHours(2);
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(15);
    private Map<String, ScrapeTarget> targets = new HashMap<>();

    @Getter
    @Setter
    public static class ScrapeTarget {
        /** HTTPS URL to fetch. */
        private String url;
        /** CSS selector whose presence/text indicates closure (e.g., {@code .beach-closed}). */
        private String closedSelector;
        /** Optional regex on page text that, if matched, marks the beach as closed. */
        private String closedRegex;
        /** Free-text reason fallback when HTML parsing can't extract one. */
        private String defaultReason;
    }
}
