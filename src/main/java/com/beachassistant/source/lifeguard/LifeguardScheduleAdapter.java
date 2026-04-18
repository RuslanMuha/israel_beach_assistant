package com.beachassistant.source.lifeguard;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceDescriptor;
import com.beachassistant.source.contract.SourceRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Pluggable external-feed tier for lifeguard schedules. When {@code beach.providers.lifeguard-feed.enabled}
 * is false (the default), the calendar path ({@link LifeguardCalendarMaterializer}) is the only
 * source of truth; when true, concrete integrations can subclass this adapter or replace it.
 *
 * <p>This default implementation returns an empty success so the scheduler can wire it without
 * producing noise.</p>
 */
@Component
@ConditionalOnProperty(prefix = "beach.providers.lifeguard-feed", name = "enabled",
        havingValue = "true")
public class LifeguardScheduleAdapter implements SourceAdapter<LifeguardScheduleRecord> {

    @Override
    public SourceType sourceType() {
        return SourceType.LIFEGUARD_SCHEDULE;
    }

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                "lifeguard-feed",
                sourceType(),
                "Lifeguard schedule external feed",
                Duration.ofHours(6),
                "LIFEGUARD_SCHEDULE"
        );
    }

    @Override
    public FetchResult<LifeguardScheduleRecord> fetch(SourceRequest request) {
        // TODO: integrate with municipal/union feed when available. For now we short-circuit so
        // the calendar materializer remains the authoritative source.
        return FetchResult.success(sourceType(), List.of());
    }
}
