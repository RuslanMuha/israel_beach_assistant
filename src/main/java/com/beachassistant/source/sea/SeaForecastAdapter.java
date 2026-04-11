package com.beachassistant.source.sea;

import com.beachassistant.common.enums.SeaRiskLevel;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceRequest;
import com.beachassistant.source.openmeteo.OpenMeteoClient;
import com.beachassistant.source.openmeteo.OpenMeteoHourlyTime;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Marine + local weather from <a href="https://open-meteo.com">Open-Meteo</a> (no API key).
 */
@Slf4j
@Component
public class SeaForecastAdapter implements SourceAdapter<SeaForecastRecord> {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final BeachProvidersProperties props;
    private final BeachRepository beachRepository;
    private final OpenMeteoClient openMeteoClient;

    public SeaForecastAdapter(BeachProvidersProperties props,
                              BeachRepository beachRepository,
                              OpenMeteoClient openMeteoClient) {
        this.props = props;
        this.beachRepository = beachRepository;
        this.openMeteoClient = openMeteoClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.SEA_FORECAST;
    }

    @Override
    public FetchResult<SeaForecastRecord> fetch(SourceRequest request) {
        if (props.isStub()) {
            return stubFetch(request);
        }
        try {
            BeachEntity beach = beachRepository.findBySlugAndActiveTrue(request.getBeachSlug())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown beach slug: " + request.getBeachSlug()));
            if (beach.getLatitude() == null || beach.getLongitude() == null) {
                return FetchResult.failure(sourceType(), "Beach has no coordinates: " + request.getBeachSlug());
            }
            double lat = beach.getLatitude();
            double lon = beach.getLongitude();

            var marinePr = openMeteoClient.fetchMarine(lat, lon);
            var forecastPr = openMeteoClient.fetchForecast(lat, lon);
            JsonNode marine = marinePr.json();
            JsonNode forecast = forecastPr.json();

            List<String> httpWarnings = new ArrayList<>();
            if (marinePr.staleFallback()) {
                httpWarnings.add("STALE_HTTP_FALLBACK");
            }
            if (forecastPr.staleFallback()) {
                httpWarnings.add("STALE_HTTP_FALLBACK");
            }
            if (marinePr.shortCircuit()) {
                httpWarnings.add("HTTP_RESPONSE_SHORT_CIRCUIT");
            }
            if (forecastPr.shortCircuit()) {
                httpWarnings.add("HTTP_RESPONSE_SHORT_CIRCUIT");
            }

            JsonNode mHourly = marine.path("hourly");
            JsonNode fHourly = forecast.path("hourly");
            if (!mHourly.has("time") || mHourly.path("time").size() == 0) {
                return FetchResult.failure(sourceType(), "Open-Meteo marine: empty hourly");
            }
            if (!fHourly.has("time") || fHourly.path("time").size() == 0) {
                return FetchResult.failure(sourceType(), "Open-Meteo forecast: empty hourly");
            }
            int idx = 0;
            ZonedDateTime validFrom = OpenMeteoHourlyTime.parse(mHourly.path("time").get(idx).asText(), ISRAEL);
            ZonedDateTime validTo = validFrom.plusHours(3);
            ZonedDateTime capturedAt = ZonedDateTime.now(ISRAEL);

            double waveM = mHourly.path("wave_height").get(idx).asDouble();
            Double seaTemp = optionalDouble(mHourly.path("sea_surface_temperature"), idx);

            double airC = fHourly.path("temperature_2m").get(idx).asDouble();
            Double humidity = optionalDouble(fHourly.path("relative_humidity_2m"), idx);
            Double uvIndex = optionalDouble(fHourly.path("uv_index"), idx);
            double windKmh = fHourly.path("wind_speed_10m").get(idx).asDouble();
            double windMps = windKmh / 3.6;
            double windDeg = fHourly.path("wind_direction_10m").get(idx).asDouble();
            String windCompass = degreesToCompass8(windDeg);

            SeaRiskLevel risk = classifyRisk(waveM, windMps);

            String raw = "{\"openMeteo\":{\"marine\":"
                    + marine.toString()
                    + ",\"forecast\":"
                    + forecast.toString()
                    + "}}";

            SeaForecastRecord record = SeaForecastRecord.builder()
                    .beachSlug(request.getBeachSlug())
                    .capturedAt(capturedAt)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .seaRiskLevel(risk)
                    .waveHeightM(waveM)
                    .windSpeedMps(windMps)
                    .windDirection(windCompass)
                    .airTemperatureC(airC)
                    .relativeHumidityPct(humidity)
                    .uvIndex(uvIndex)
                    .seaTemperatureC(seaTemp)
                    .intervalIsInferred(false)
                    .rawPayloadJson(raw)
                    .build();
            return FetchResult.success(sourceType(), List.of(record), httpWarnings);
        } catch (Exception e) {
            log.warn("Sea forecast fetch failed for beach={}: {}", request.getBeachSlug(), e.getMessage());
            return FetchResult.failure(sourceType(), e.getMessage());
        }
    }

    private static Double optionalDouble(JsonNode arr, int idx) {
        if (arr == null || !arr.isArray() || arr.size() <= idx || arr.get(idx).isNull()) {
            return null;
        }
        return arr.get(idx).asDouble();
    }

    private static String degreesToCompass8(double deg) {
        String[] dirs = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int idx = (int) (Math.floor(((deg % 360) + 22.5) / 45.0)) & 7;
        return dirs[idx];
    }

    static SeaRiskLevel classifyRisk(double waveM, double windMps) {
        SeaRiskLevel base;
        if (waveM < 0.6) {
            base = SeaRiskLevel.CALM;
        } else if (waveM < 1.1) {
            base = SeaRiskLevel.LOW;
        } else if (waveM < 1.8) {
            base = SeaRiskLevel.MODERATE;
        } else if (waveM < 2.6) {
            base = SeaRiskLevel.HIGH;
        } else {
            base = SeaRiskLevel.SEVERE;
        }
        if (windMps >= 14) {
            base = bump(bump(base));
        } else if (windMps >= 10) {
            base = bump(base);
        }
        return base;
    }

    private static SeaRiskLevel bump(SeaRiskLevel r) {
        return switch (r) {
            case CALM -> SeaRiskLevel.LOW;
            case LOW -> SeaRiskLevel.MODERATE;
            case MODERATE -> SeaRiskLevel.HIGH;
            case HIGH, SEVERE -> SeaRiskLevel.SEVERE;
        };
    }

    private FetchResult<SeaForecastRecord> stubFetch(SourceRequest request) {
        log.info("Stub sea forecast for beach={}", request.getBeachSlug());
        ZonedDateTime now = ZonedDateTime.now(ISRAEL);
        SeaForecastRecord record = SeaForecastRecord.builder()
                .beachSlug(request.getBeachSlug())
                .capturedAt(now)
                .validFrom(now)
                .validTo(now.plusHours(3))
                .seaRiskLevel(SeaRiskLevel.CALM)
                .waveHeightM(0.3)
                .windSpeedMps(3.0)
                .windDirection("NW")
                .airTemperatureC(24.0)
                .relativeHumidityPct(55.0)
                .uvIndex(6.0)
                .seaTemperatureC(21.0)
                .intervalIsInferred(false)
                .rawPayloadJson("{\"stub\":true}")
                .build();
        return FetchResult.success(sourceType(), List.of(record));
    }
}
