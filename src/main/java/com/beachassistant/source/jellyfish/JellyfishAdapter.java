package com.beachassistant.source.jellyfish;

import com.beachassistant.common.enums.JellyfishSeverity;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceRequest;
import com.beachassistant.source.inaturalist.InaturalistClient;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Recent jellyfish-related observations from <a href="https://www.inaturalist.org">iNaturalist</a> (citizen science).
 */
@Slf4j
@Component
public class JellyfishAdapter implements SourceAdapter<JellyfishRecord> {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final BeachProvidersProperties props;
    private final BeachRepository beachRepository;
    private final InaturalistClient inaturalistClient;

    public JellyfishAdapter(BeachProvidersProperties props,
                            BeachRepository beachRepository,
                            InaturalistClient inaturalistClient) {
        this.props = props;
        this.beachRepository = beachRepository;
        this.inaturalistClient = inaturalistClient;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.JELLYFISH;
    }

    @Override
    public FetchResult<JellyfishRecord> fetch(SourceRequest request) {
        if (props.isStub()) {
            return stubFetch(request);
        }
        try {
            BeachEntity beach = beachRepository.findBySlugAndActiveTrue(request.getBeachSlug())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown beach slug: " + request.getBeachSlug()));
            if (!beach.isHasJellyfishSource()) {
                return noSourceRecord(request.getBeachSlug());
            }
            if (beach.getLatitude() == null || beach.getLongitude() == null) {
                return FetchResult.failure(sourceType(), "Beach has no coordinates: " + request.getBeachSlug());
            }
            var rootPr = inaturalistClient.fetchObservations(beach.getLatitude(), beach.getLongitude());
            JsonNode root = rootPr.json();
            List<String> httpWarnings = new ArrayList<>();
            if (rootPr.staleFallback()) {
                httpWarnings.add("STALE_HTTP_FALLBACK");
            }
            if (rootPr.shortCircuit()) {
                httpWarnings.add("HTTP_RESPONSE_SHORT_CIRCUIT");
            }
            int total = root.path("total_results").asInt(0);
            JsonNode results = root.path("results");
            LocalDate cutoff = LocalDate.now(ISRAEL).minusDays(14);
            int recentCount = 0;
            int researchCount = 0;
            for (JsonNode obs : results) {
                LocalDate observed = JellyfishObservedOnParser.parseObservedLocalDate(obs);
                if (observed != null && observed.isBefore(cutoff)) {
                    continue;
                }
                recentCount++;
                if ("research".equals(obs.path("quality_grade").asText())) {
                    researchCount++;
                }
            }
            JellyfishSeverity severity = mapSeverity(recentCount);
            String confidence = researchCount > 0 ? "MEDIUM" : "LOW";
            ZonedDateTime now = ZonedDateTime.now(ISRAEL);
            ZonedDateTime windowStart = now.minusDays(14);
            String raw = "{\"inaturalist\":{\"total_results\":" + total + ",\"recent14d\":" + recentCount + "}}";
            JellyfishRecord record = JellyfishRecord.builder()
                    .beachSlug(request.getBeachSlug())
                    .capturedAt(now)
                    .windowStart(windowStart)
                    .windowEnd(now)
                    .severityLevel(severity)
                    .reportCount(recentCount)
                    .confidenceLevel(confidence)
                    .rawPayloadJson(raw)
                    .build();
            return FetchResult.success(sourceType(), List.of(record), httpWarnings);
        } catch (Exception e) {
            log.warn("Jellyfish fetch failed for beach={}: {}", request.getBeachSlug(), e.getMessage());
            return FetchResult.failure(sourceType(), e.getMessage());
        }
    }

    private FetchResult<JellyfishRecord> noSourceRecord(String slug) {
        ZonedDateTime now = ZonedDateTime.now(ISRAEL);
        JellyfishRecord record = JellyfishRecord.builder()
                .beachSlug(slug)
                .capturedAt(now)
                .windowStart(now.minusDays(14))
                .windowEnd(now)
                .severityLevel(JellyfishSeverity.NONE)
                .reportCount(0)
                .confidenceLevel("N/A")
                .rawPayloadJson("{\"skipped\":true,\"reason\":\"has_jellyfish_source_false\"}")
                .build();
        return FetchResult.success(sourceType(), List.of(record));
    }

    static JellyfishSeverity mapSeverity(int recentCount) {
        if (recentCount <= 0) {
            return JellyfishSeverity.NONE;
        }
        if (recentCount <= 3) {
            return JellyfishSeverity.LOW;
        }
        if (recentCount <= 8) {
            return JellyfishSeverity.MEDIUM;
        }
        return JellyfishSeverity.HIGH;
    }

    private FetchResult<JellyfishRecord> stubFetch(SourceRequest request) {
        log.info("Stub jellyfish for beach={}", request.getBeachSlug());
        ZonedDateTime now = ZonedDateTime.now(ISRAEL);
        JellyfishRecord record = JellyfishRecord.builder()
                .beachSlug(request.getBeachSlug())
                .capturedAt(now)
                .windowStart(now.minusHours(48))
                .windowEnd(now)
                .severityLevel(JellyfishSeverity.NONE)
                .reportCount(0)
                .confidenceLevel("LOW")
                .rawPayloadJson("{\"stub\":true}")
                .build();
        return FetchResult.success(sourceType(), List.of(record));
    }
}
