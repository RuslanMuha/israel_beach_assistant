package com.beachassistant.source.lifeguard;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code beach.providers.lifeguard-calendar.*} — static seasonal lifeguard schedules per beach,
 * materialized into {@code lifeguard_schedule} rows by {@link LifeguardCalendarMaterializer}.
 *
 * <p>This is the "no external feed" path: operators describe opening hours in YAML (per beach,
 * per day-of-week, per season) and the materializer keeps the DB rows in sync. When a municipal
 * or union feed becomes available, the external {@link LifeguardScheduleAdapter} layer can
 * override these rows.</p>
 */
@Component
@ConfigurationProperties(prefix = "beach.providers.lifeguard-calendar")
@Getter
@Setter
public class LifeguardCalendarProperties {
    private boolean enabled = false;
    /** Map of beach slug → list of day windows. */
    private Map<String, List<DayWindow>> beaches = Map.of();

    @Getter
    @Setter
    public static class DayWindow {
        /** ISO day-of-week 1-7 (Mon=1). {@code null} means applies every day. */
        private Integer dayOfWeek;
        private LocalTime openTime;
        private LocalTime closeTime;
        /** Free text label (e.g., "summer"); used only for operator clarity. */
        private String season;
    }

    /** Defensive access; callers receive a non-null list. */
    public List<DayWindow> windowsFor(String slug) {
        return beaches.getOrDefault(slug, new ArrayList<>());
    }
}
