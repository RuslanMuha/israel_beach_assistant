package com.beachassistant.config;

import com.beachassistant.common.enums.SourceType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

@ConfigurationProperties(prefix = "beach.freshness")
@Getter
@Setter
public class FreshnessProperties {

    private long defaultFreshHours = 24;
    private long defaultStaleHours = 72;

    private Map<String, Long> freshHoursOverride = new java.util.HashMap<>();
    private Map<String, Long> staleHoursOverride = new java.util.HashMap<>();

    public long getFreshThresholdHours(SourceType sourceType) {
        return freshHoursOverride.getOrDefault(sourceType.name(), defaultFreshHours);
    }

    public long getStaleThresholdHours(SourceType sourceType) {
        return staleHoursOverride.getOrDefault(sourceType.name(), defaultStaleHours);
    }
}
