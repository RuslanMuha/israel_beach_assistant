package com.beachassistant.source.advisory;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceDescriptor;
import com.beachassistant.source.contract.SourceRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Skeleton for bathing-water-quality advisories (lab-based). Disabled by default; a concrete
 * implementation (Ministry of Health feed or municipal endpoint) should subclass or replace this
 * adapter. Shipping the stub lets the scheduler, decision engine, and UI wire against a stable
 * {@link SourceType#WATER_QUALITY_ADVISORY} contract without waiting for the feed integration.
 */
@Component
@ConditionalOnProperty(prefix = "beach.providers.water-quality", name = "enabled", havingValue = "true")
public class WaterQualityAdvisoryAdapter implements SourceAdapter<WaterQualityAdvisoryRecord> {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    @Override
    public SourceType sourceType() {
        return SourceType.WATER_QUALITY_ADVISORY;
    }

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                "water-quality-advisory",
                sourceType(),
                "Bathing water-quality advisory",
                Duration.ofHours(6),
                "WATER_QUALITY_ADVISORY"
        );
    }

    @Override
    public FetchResult<WaterQualityAdvisoryRecord> fetch(SourceRequest request) {
        ZonedDateTime now = ZonedDateTime.now(ISRAEL);
        WaterQualityAdvisoryRecord record = WaterQualityAdvisoryRecord.builder()
                .beachSlug(request.getBeachSlug())
                .active(false)
                .advisoryLevel("UNKNOWN")
                .message(null)
                .validFrom(now)
                .validTo(now.plusHours(24))
                .capturedAt(now)
                .rawPayloadJson("{\"skeleton\":true}")
                .build();
        return FetchResult.success(sourceType(), List.of(record));
    }
}
