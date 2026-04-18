package com.beachassistant.source.lifeguard;

import com.beachassistant.common.enums.ScheduleType;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.LifeguardScheduleEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.LifeguardScheduleRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * On startup, and hourly thereafter, inserts {@link ScheduleType#REGULAR} schedule rows for any
 * calendar entry in {@link LifeguardCalendarProperties} that doesn't already have a matching row
 * in the last 24 hours. Idempotent: the composite of (beach, dayOfWeek, openTime, closeTime) is
 * used to detect duplicates.
 *
 * <p>External-feed adapters (e.g. future {@link LifeguardScheduleAdapter}) always write with
 * {@link ScheduleType#OVERRIDE} and a newer {@code capturedAt} so they win in
 * {@code findActiveSchedulesForDate}.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "beach.providers.lifeguard-calendar", name = "enabled",
        havingValue = "true")
public class LifeguardCalendarMaterializer {

    private final LifeguardCalendarProperties props;
    private final BeachRepository beaches;
    private final LifeguardScheduleRepository schedules;
    private final Clock clock;

    public LifeguardCalendarMaterializer(LifeguardCalendarProperties props,
                                         BeachRepository beaches,
                                         LifeguardScheduleRepository schedules,
                                         Clock clock) {
        this.props = props;
        this.beaches = beaches;
        this.schedules = schedules;
        this.clock = clock;
    }

    @PostConstruct
    void atStartup() {
        try {
            materialize();
        } catch (Exception e) {
            log.warn("Lifeguard calendar materialization at startup failed: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void materialize() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        for (BeachEntity beach : beaches.findAllByActiveTrue()) {
            List<LifeguardCalendarProperties.DayWindow> windows = props.windowsFor(beach.getSlug());
            for (LifeguardCalendarProperties.DayWindow window : windows) {
                if (window.getOpenTime() == null || window.getCloseTime() == null) {
                    continue;
                }
                boolean alreadyPresent = schedules
                        .findActiveSchedulesForDate(beach.getId(), now.toLocalDate()).stream()
                        .anyMatch(existing -> sameWindow(existing, window));
                if (alreadyPresent) continue;

                LifeguardScheduleEntity row = new LifeguardScheduleEntity();
                row.setBeach(beach);
                row.setScheduleType(ScheduleType.REGULAR);
                row.setDayOfWeek(window.getDayOfWeek());
                row.setOpenTime(window.getOpenTime());
                row.setCloseTime(window.getCloseTime());
                row.setActive(true);
                row.setSourceType(SourceType.LIFEGUARD_SCHEDULE);
                row.setCapturedAt(now);
                schedules.save(row);
            }
        }
    }

    private static boolean sameWindow(LifeguardScheduleEntity row,
                                      LifeguardCalendarProperties.DayWindow window) {
        return java.util.Objects.equals(row.getDayOfWeek(), window.getDayOfWeek())
                && java.util.Objects.equals(row.getOpenTime(), window.getOpenTime())
                && java.util.Objects.equals(row.getCloseTime(), window.getCloseTime());
    }
}
