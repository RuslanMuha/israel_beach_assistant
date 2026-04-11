package com.beachassistant.decision.freshness;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.FreshnessProperties;
import org.springframework.stereotype.Service;

import java.time.ZonedDateTime;

@Service
public class FreshnessService {

    private final FreshnessProperties properties;

    public FreshnessService(FreshnessProperties properties) {
        this.properties = properties;
    }

    public FreshnessStatus classify(ZonedDateTime capturedAt, SourceType sourceType) {
        if (capturedAt == null) {
            return FreshnessStatus.EXPIRED;
        }
        long ageHours = java.time.Duration.between(capturedAt, ZonedDateTime.now()).toHours();
        long freshThreshold = properties.getFreshThresholdHours(sourceType);
        long staleThreshold = properties.getStaleThresholdHours(sourceType);

        if (ageHours <= freshThreshold) {
            return FreshnessStatus.FRESH;
        } else if (ageHours <= staleThreshold) {
            return FreshnessStatus.STALE;
        } else {
            return FreshnessStatus.EXPIRED;
        }
    }

    public FreshnessStatus worstCase(Iterable<FreshnessStatus> statuses) {
        FreshnessStatus worst = FreshnessStatus.FRESH;
        for (FreshnessStatus s : statuses) {
            if (s == FreshnessStatus.EXPIRED) {
                return FreshnessStatus.EXPIRED;
            }
            if (s == FreshnessStatus.STALE) {
                worst = FreshnessStatus.STALE;
            }
        }
        return worst;
    }
}
