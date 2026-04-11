package com.beachassistant.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * External data providers (Open-Meteo, iNaturalist). Set {@code beach.providers.stub=true} for offline tests.
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "beach.providers")
public class BeachProvidersProperties {

    /**
     * When true, adapters return deterministic synthetic data (integration tests, air-gapped CI).
     */
    private boolean stub = false;

    private String openMeteoMarineUrl = "https://marine-api.open-meteo.com/v1/marine";
    private String openMeteoForecastUrl = "https://api.open-meteo.com/v1/forecast";
    private String openMeteoAirQualityUrl = "https://air-quality-api.open-meteo.com/v1/air-quality";

    private String timezone = "Asia/Jerusalem";

    /** European AQI: values above this trigger an active advisory (CAMS / Open-Meteo). */
    private int airQualityAdvisoryThreshold = 50;

    private String inaturalistBaseUrl = "https://api.inaturalist.org/v1";

    /** iNaturalist taxon IDs (e.g. true jellyfish Scyphozoa + common species). */
    private List<Integer> jellyfishTaxonIds = List.of(48332, 319371);

    /** Search radius in km for jellyfish observations around the beach. */
    private double jellyfishSearchRadiusKm = 12.0;

    private int httpTimeoutSeconds = 15;

    /**
     * Max bytes buffered for a single HTTP response body (WebClient). iNaturalist JSON for
     * {@code per_page=50} often exceeds Spring's default 256 KiB limit.
     */
    @Min(65_536)
    @Max(104_857_600)
    private int httpMaxResponseBufferBytes = 5_242_880;
}
