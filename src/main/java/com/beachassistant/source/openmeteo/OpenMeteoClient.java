package com.beachassistant.source.openmeteo;

import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.integration.IntegrationSourceKey;
import com.beachassistant.integration.http.OutboundHttpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

/**
 * Open-Meteo: marine forecast, weather (wind/air), and CAMS-based air quality (no API key).
 */
@Slf4j
@Component
public class OpenMeteoClient {

    private final OutboundHttpService outboundHttpService;
    private final BeachProvidersProperties props;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(OutboundHttpService outboundHttpService,
                           BeachProvidersProperties props,
                           ObjectMapper objectMapper) {
        this.outboundHttpService = outboundHttpService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    /**
     * Parsed JSON plus flags when the resilient client served short-circuit or stale fallback bodies.
     */
    public ParsedResponse fetchMarine(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(props.getOpenMeteoMarineUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "wave_height,sea_surface_temperature")
                .queryParam("forecast_hours", 24)
                .queryParam("timezone", props.getTimezone())
                .build()
                .toUri();
        return getParsed(uri, "marine");
    }

    public ParsedResponse fetchForecast(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(props.getOpenMeteoForecastUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "temperature_2m,relative_humidity_2m,uv_index,wind_speed_10m,wind_direction_10m")
                .queryParam("forecast_hours", 24)
                .queryParam("timezone", props.getTimezone())
                .build()
                .toUri();
        return getParsed(uri, "forecast");
    }

    public ParsedResponse fetchAirQuality(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(props.getOpenMeteoAirQualityUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "european_aqi,pm2_5,pm10")
                .queryParam("forecast_hours", 12)
                .queryParam("timezone", props.getTimezone())
                .build()
                .toUri();
        return getParsed(uri, "air_quality");
    }

    private ParsedResponse getParsed(URI uri, String operation) {
        var outcome = outboundHttpService.getJson(IntegrationSourceKey.OPEN_METEO, uri, operation);
        if (!outcome.success()) {
            throw new IllegalStateException("Open-Meteo " + operation + " fetch failed: " + outcome.errorMessage());
        }
        try {
            JsonNode tree = objectMapper.readTree(outcome.body());
            return new ParsedResponse(tree, outcome.staleFallback(), outcome.shortCircuit());
        } catch (Exception e) {
            throw new IllegalStateException("Open-Meteo JSON parse failed: " + e.getMessage(), e);
        }
    }

    public record ParsedResponse(JsonNode json, boolean staleFallback, boolean shortCircuit) {
    }
}
