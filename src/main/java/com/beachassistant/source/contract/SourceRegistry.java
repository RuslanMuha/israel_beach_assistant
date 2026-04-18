package com.beachassistant.source.contract;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Central registry over all {@link SourceAdapter} beans. Built at startup, it lets the scheduler
 * (and future admin endpoints) iterate descriptors without core switch/case changes when new
 * adapters are added.
 */
@Slf4j
@Component
public class SourceRegistry {

    private final List<SourceAdapter<?>> adapters;
    private final Map<String, SourceAdapter<?>> byId;
    private final SourceProviderProperties providerProperties;

    public SourceRegistry(List<SourceAdapter<?>> adapters, SourceProviderProperties providerProperties) {
        this.adapters = adapters;
        this.providerProperties = providerProperties;
        Map<String, SourceAdapter<?>> byIdMap = new HashMap<>();
        for (SourceAdapter<?> a : adapters) {
            SourceDescriptor d = a.descriptor();
            SourceAdapter<?> prev = byIdMap.put(d.id(), a);
            if (prev != null) {
                log.warn("Duplicate SourceAdapter id '{}': {} replaces {}", d.id(),
                        a.getClass().getSimpleName(), prev.getClass().getSimpleName());
            }
        }
        this.byId = Collections.unmodifiableMap(byIdMap);
        log.info("Discovered {} source adapters: {}", byId.size(), byId.keySet());
    }

    public List<SourceAdapter<?>> adapters() {
        return List.copyOf(adapters);
    }

    public Optional<SourceAdapter<?>> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<SourceAdapter<?>> enabledAdapters() {
        return adapters.stream()
                .filter(a -> providerProperties.isEnabled(a.descriptor().id()))
                .toList();
    }

    public List<SourceAdapter<?>> findMatching(Predicate<SourceDescriptor> filter) {
        return adapters.stream().filter(a -> filter.test(a.descriptor())).toList();
    }
}
