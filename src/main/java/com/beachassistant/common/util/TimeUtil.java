package com.beachassistant.common.util;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class TimeUtil {

    public static final ZoneId ISRAEL_ZONE = ZoneId.of("Asia/Jerusalem");
    public static final DateTimeFormatter DISPLAY_FORMATTER =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ISRAEL_ZONE);

    private static final DateTimeFormatter CLOCK_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm").withZone(ISRAEL_ZONE);

    private TimeUtil() {
    }

    public static ZonedDateTime nowInIsrael() {
        return ZonedDateTime.now(ISRAEL_ZONE);
    }

    public static String formatForDisplay(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return "—";
        }
        return DISPLAY_FORMATTER.format(dateTime);
    }

    /** Short clock time for compact cards (e.g. {@code 20:31}). */
    public static String formatTimeClock(ZonedDateTime dateTime) {
        if (dateTime == null) {
            return "—";
        }
        return CLOCK_FORMATTER.format(dateTime);
    }

    public static long ageInHours(ZonedDateTime capturedAt) {
        if (capturedAt == null) {
            return Long.MAX_VALUE;
        }
        ZonedDateTime now = ZonedDateTime.now(capturedAt.getZone());
        return java.time.Duration.between(capturedAt, now).toHours();
    }
}
