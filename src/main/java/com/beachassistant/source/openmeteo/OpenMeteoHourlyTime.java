package com.beachassistant.source.openmeteo;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class OpenMeteoHourlyTime {

    private static final DateTimeFormatter WITHOUT_SECONDS =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    private OpenMeteoHourlyTime() {
    }

    public static ZonedDateTime parse(String iso, ZoneId zone) {
        try {
            return LocalDateTime.parse(iso, WITHOUT_SECONDS).atZone(zone);
        } catch (DateTimeParseException ignored) {
            // fall through
        }
        try {
            return LocalDateTime.parse(iso, DateTimeFormatter.ISO_LOCAL_DATE_TIME).atZone(zone);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Unparseable Open-Meteo hourly time: " + iso, e);
        }
    }
}
