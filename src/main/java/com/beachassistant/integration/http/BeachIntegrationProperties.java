package com.beachassistant.integration.http;

import com.beachassistant.integration.IntegrationSourceKey;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Outbound HTTP resilience, caching, ingestion pacing, and scheduler safety.
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "beach.integration")
public class BeachIntegrationProperties {

    private HttpSourceProperties openMeteo = new HttpSourceProperties();
    private HttpSourceProperties inaturalist = new HttpSourceProperties();
    private HttpSourceProperties camera = new HttpSourceProperties();
    private IngestionConcurrencyProperties ingestion = new IngestionConcurrencyProperties();
    private SchedulerSafetyProperties scheduler = new SchedulerSafetyProperties();

    /**
     * Caffeine expiry for cached JSON response bodies (short-circuit + stale fallback source).
     */
    private Duration httpResponseCacheTtl = Duration.ofHours(6);

    public HttpSourceProperties forSource(IntegrationSourceKey key) {
        return switch (key) {
            case OPEN_METEO -> openMeteo;
            case INATURALIST -> inaturalist;
            case CAMERA -> camera;
        };
    }
}
