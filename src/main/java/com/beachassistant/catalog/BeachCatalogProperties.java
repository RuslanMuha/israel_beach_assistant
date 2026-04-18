package com.beachassistant.catalog;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * YAML-driven beach catalog. Operators can add beaches without a Flyway migration by dropping
 * entries under {@code beach.catalog.*} in application.yml (or a profile override). Flyway stays
 * the source of truth for the initial seed; catalog entries are merged on startup by
 * {@link BeachCatalogLoader} via idempotent upsert.
 */
@ConfigurationProperties(prefix = "beach.catalog")
@Getter
@Setter
public class BeachCatalogProperties {

    /** When false, skips the loader; seeding relies on Flyway alone. */
    private boolean enabled = false;

    private List<BeachEntry> beaches = new ArrayList<>();

    @Getter
    @Setter
    public static class BeachEntry {
        /** Stable URL-friendly identifier (e.g. {@code "lido-beach"}). Required. */
        private String slug;

        /** Display name shown to users. */
        private String displayName;

        /** City name; the city must already exist (seeded via Flyway). */
        private String city;

        /** Aliases (case-insensitive) that users may type. */
        private List<String> aliases = new ArrayList<>();

        private Double latitude;
        private Double longitude;
        private boolean active = true;
        private boolean supportsSwimming = true;
        private boolean hasLifeguards = false;
        private boolean hasCamera = false;
        private boolean hasJellyfishSource = false;
        private String notes;
    }
}
