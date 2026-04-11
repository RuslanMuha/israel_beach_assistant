package com.beachassistant.source.openmeteo;

import com.beachassistant.config.BeachProvidersProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;

/**
 * Open-Meteo: marine forecast, weather (wind/air), and CAMS-based air quality (no API key).
 */
@Slf4j
@Component
public class OpenMeteoClient {

    private final WebClient webClient;
    private final BeachProvidersProperties props;
    private final ObjectMapper objectMapper;

    public OpenMeteoClient(WebClient.Builder webClientBuilder,
                           BeachProvidersProperties props,
                           ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public JsonNode fetchMarine(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(props.getOpenMeteoMarineUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "wave_height,sea_surface_temperature")
                .queryParam("forecast_hours", 24)
                .queryParam("timezone", props.getTimezone())
                .build()
                .toUri();
        return getJson(uri);
    }

    public JsonNode fetchForecast(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(props.getOpenMeteoForecastUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "temperature_2m,relative_humidity_2m,uv_index,wind_speed_10m,wind_direction_10m")
                .queryParam("forecast_hours", 24)
                .queryParam("timezone", props.getTimezone())
                .build()
                .toUri();
        return getJson(uri);
    }

    public JsonNode fetchAirQuality(double latitude, double longitude) {
        URI uri = UriComponentsBuilder.fromUriString(props.getOpenMeteoAirQualityUrl())
                .queryParam("latitude", latitude)
                .queryParam("longitude", longitude)
                .queryParam("hourly", "european_aqi,pm2_5,pm10")
                .queryParam("forecast_hours", 12)
                .queryParam("timezone", props.getTimezone())
                .build()
                .toUri();
        return getJson(uri);
    }

    private JsonNode getJson(URI uri) {
        Duration timeout = Duration.ofSeconds(props.getHttpTimeoutSeconds());
        String body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .block(timeout.plusSeconds(2));
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Open-Meteo JSON parse failed: " + e.getMessage(), e);
        }
    }
}
