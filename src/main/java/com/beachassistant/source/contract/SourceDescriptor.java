package com.beachassistant.source.contract;

import com.beachassistant.common.enums.SourceType;

import java.time.Duration;

/**
 * Declarative metadata about a {@link SourceAdapter}. Designed so new adapters can be dropped
 * in without core changes: the scheduler can read the cadence from the descriptor, feature flags
 * follow a naming convention keyed on {@link #id()}, and docs/admin endpoints can enumerate
 * registered sources.
 *
 * @param id short, stable identifier (e.g. {@code "sea-forecast"}); also the config key used in
 *           {@code beach.providers.<id>.enabled}
 * @param sourceType legacy enum backing this descriptor, for incremental migration
 * @param displayName human-readable name used in admin/observability surfaces
 * @param defaultCadence how often the descriptor would like to be polled; the scheduler may honor
 *                       or override via {@code beach.providers.<id>.cadence}
 * @param freshnessProfile name of freshness override profile, e.g. {@code "SEA_FORECAST"}
 */
public record SourceDescriptor(
        String id,
        SourceType sourceType,
        String displayName,
        Duration defaultCadence,
        String freshnessProfile
) {
    public static SourceDescriptor legacy(SourceType type, Duration cadence) {
        return new SourceDescriptor(
                type.name().toLowerCase().replace('_', '-'),
                type,
                type.name(),
                cadence,
                type.name()
        );
    }
}
