package com.beachassistant.source.contract;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration read from {@code beach.providers.<id>.*}. Each adapter's descriptor is keyed
 * on {@code id}; an entry controls whether the adapter runs, and optionally overrides its
 * scheduling cadence.
 *
 * <p>Existing {@code beach.providers.stub} (legacy boolean) is deliberately preserved for
 * backwards compatibility by other properties classes.</p>
 */
@ConfigurationProperties(prefix = "beach.providers")
@Getter
@Setter
public class SourceProviderProperties {

    /** Per-adapter toggles keyed on {@link SourceDescriptor#id()}. */
    private Map<String, Entry> adapters = new HashMap<>();

    public Entry entryFor(String id) {
        return adapters.getOrDefault(id, Entry.DEFAULTS);
    }

    public boolean isEnabled(String id) {
        return entryFor(id).enabled;
    }

    @Getter
    @Setter
    public static class Entry {
        public static final Entry DEFAULTS = new Entry();

        /** Adapter is polled when true; defaults to true for legacy adapters. */
        private boolean enabled = true;

        /** Overrides {@link SourceDescriptor#defaultCadence()} when non-null. */
        private Duration cadence;
    }
}
