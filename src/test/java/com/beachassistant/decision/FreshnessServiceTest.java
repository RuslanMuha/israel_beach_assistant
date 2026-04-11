package com.beachassistant.decision;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.FreshnessProperties;
import com.beachassistant.decision.freshness.FreshnessService;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class FreshnessServiceTest {

    private final FreshnessService service;

    FreshnessServiceTest() {
        FreshnessProperties props = new FreshnessProperties();
        service = new FreshnessService(props);
    }

    @Test
    void nullCapturedAt_returnsExpired() {
        assertThat(service.classify(null, SourceType.SEA_FORECAST)).isEqualTo(FreshnessStatus.EXPIRED);
    }

    @Test
    void recentTimestamp_returnsFresh() {
        ZonedDateTime recent = ZonedDateTime.now().minusHours(1);
        assertThat(service.classify(recent, SourceType.HEALTH_ADVISORY)).isEqualTo(FreshnessStatus.FRESH);
    }

    @Test
    void olderThan24h_returnsStale() {
        ZonedDateTime old = ZonedDateTime.now().minusHours(36);
        assertThat(service.classify(old, SourceType.HEALTH_ADVISORY)).isEqualTo(FreshnessStatus.STALE);
    }

    @Test
    void olderThan72h_returnsExpired() {
        ZonedDateTime veryOld = ZonedDateTime.now().minusHours(80);
        assertThat(service.classify(veryOld, SourceType.HEALTH_ADVISORY)).isEqualTo(FreshnessStatus.EXPIRED);
    }

    @Test
    void worstCase_returnsExpiredIfAnyExpired() {
        assertThat(service.worstCase(
                java.util.List.of(FreshnessStatus.FRESH, FreshnessStatus.EXPIRED, FreshnessStatus.STALE)
        )).isEqualTo(FreshnessStatus.EXPIRED);
    }

    @Test
    void worstCase_returnsStaleIfNoExpired() {
        assertThat(service.worstCase(
                java.util.List.of(FreshnessStatus.FRESH, FreshnessStatus.STALE)
        )).isEqualTo(FreshnessStatus.STALE);
    }

    @Test
    void worstCase_returnsFreshIfAllFresh() {
        assertThat(service.worstCase(
                java.util.List.of(FreshnessStatus.FRESH, FreshnessStatus.FRESH)
        )).isEqualTo(FreshnessStatus.FRESH);
    }
}
