package com.beachassistant.source.advisory;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceDescriptor;
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
 * Air-quality advisory from CAMS European AQI via <a href="https://open-meteo.com">Open-Meteo</a>
 * (near-shore air quality; not a substitute for Ministry of Health bathing-water lab tests).
 *
 * <p>Formerly named "HealthAdvisory" before the water-quality advisory split; the class name
 * is kept for binary compatibility with persisted records and prior versions, but the
 * descriptor id ({@code air-quality-advisory}) is the canonical identifier going forward.</p>
 */
@Slf4j
@Component
public class HealthAdvisoryAdapter implements SourceAdapter<HealthAdvisoryRecord> {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final BeachProvidersProperties props;
    private final BeachRepository beachRepository;
    private final OpenMeteoClient openMeteoClient;

    public HealthAdvisoryAdapter(BeachProvidersProperties props,
                                 BeachRepository beachRepository,
                                 OpenMeteoClient openMeteoClient) {
        this.props = props;
        this.beachRepository = beachRepository;
        this.openMeteoClient = openMeteoClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.HEALTH_ADVISORY;
    }

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                "air-quality-advisory",
                sourceType(),
                "Open-Meteo CAMS air-quality advisory",
                java.time.Duration.ofHours(1),
                "HEALTH_ADVISORY"
        );
    }

    @Override
    public FetchResult<HealthAdvisoryRecord> fetch(SourceRequest request) {
        if (props.isStub()) {
            return stubFetch(request);
        }
        try {
            BeachEntity beach = beachRepository.findBySlugAndActiveTrue(request.getBeachSlug())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown beach slug: " + request.getBeachSlug()));
            if (beach.getLatitude() == null || beach.getLongitude() == null) {
                return FetchResult.failure(sourceType(), "Beach has no coordinates: " + request.getBeachSlug());
            }
            double lat = props.advisoryProviderLocationForBeach(beach.getSlug())
                    .map(BeachProvidersProperties.ProviderLocation::getLatitude)
                    .orElse(beach.getLatitude());
            double lon = props.advisoryProviderLocationForBeach(beach.getSlug())
                    .map(BeachProvidersProperties.ProviderLocation::getLongitude)
                    .orElse(beach.getLongitude());
            var aqPr = openMeteoClient.fetchAirQuality(lat, lon);
            JsonNode aq = aqPr.json();
            List<String> httpWarnings = new ArrayList<>();
            if (aqPr.staleFallback()) {
                httpWarnings.add("STALE_HTTP_FALLBACK");
            }
            if (aqPr.shortCircuit()) {
                httpWarnings.add("HTTP_RESPONSE_SHORT_CIRCUIT");
            }
            JsonNode hourly = aq.path("hourly");
            if (!hourly.has("time") || hourly.path("time").size() == 0) {
                return FetchResult.failure(sourceType(), "Open-Meteo air quality: empty hourly");
            }
            int idx = 0;
            ZonedDateTime validFrom = OpenMeteoHourlyTime.parse(hourly.path("time").get(idx).asText(), ISRAEL);
            ZonedDateTime validTo = validFrom.plusHours(6);
            int eaqi = hourly.path("european_aqi").get(idx).asInt();
            boolean active = eaqi >= props.getAirQualityAdvisoryThreshold();
            String message = active
                    ? ("Индекс качества воздуха CAMS повышен рядом с пляжем (EAQI " + eaqi + "). "
                    + "Людям из чувствительных групп рекомендуется ограничить длительную физическую нагрузку на улице. "
                    + "Источник: Open-Meteo Air Quality (CAMS).")
                    : null;
            ZonedDateTime capturedAt = ZonedDateTime.now(ISRAEL);
            String raw = "{\"openMeteoAirQuality\":" + aq + "}";
            HealthAdvisoryRecord record = HealthAdvisoryRecord.builder()
                    .beachSlug(request.getBeachSlug())
                    .capturedAt(capturedAt)
                    .validFrom(validFrom)
                    .validTo(validTo)
                    .active(active)
                    .advisoryType(active ? "AIR_QUALITY_EAQI" : "NONE")
                    .message(message)
                    .rawPayloadJson(raw)
                    .build();
            return FetchResult.success(sourceType(), List.of(record), httpWarnings);
        } catch (Exception e) {
            log.warn("Health advisory fetch failed for beach={}: {}", request.getBeachSlug(), e.getMessage());
            return FetchResult.failure(sourceType(), e.getMessage());
        }
    }

    public String providerLocationKeyForBeach(BeachEntity beach) {
        return props.getAdvisoryProviderLocationByBeach().getOrDefault(
                beach.getSlug(),
                "coords:" + beach.getLatitude() + "," + beach.getLongitude()
        );
    }

    private FetchResult<HealthAdvisoryRecord> stubFetch(SourceRequest request) {
        ZonedDateTime now = ZonedDateTime.now(ISRAEL);
        HealthAdvisoryRecord record = HealthAdvisoryRecord.builder()
                .beachSlug(request.getBeachSlug())
                .capturedAt(now)
                .validFrom(now)
                .validTo(now.plusHours(6))
                .active(false)
                .advisoryType("NONE")
                .message(null)
                .rawPayloadJson("{\"stub\":true}")
                .build();
        return FetchResult.success(sourceType(), List.of(record));
    }
}
