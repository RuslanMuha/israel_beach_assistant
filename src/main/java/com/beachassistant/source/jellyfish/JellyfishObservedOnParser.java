package com.beachassistant.source.jellyfish;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Resolves observation calendar date from iNaturalist observation JSON.
 */
public final class JellyfishObservedOnParser {

    private JellyfishObservedOnParser() {
    }

    /**
     * @return local calendar date of observation, or {@code null} if unknown
     */
    public static LocalDate parseObservedLocalDate(JsonNode observation) {
        if (observation == null || observation.isMissingNode()) {
            return null;
        }
        String observedOn = observation.path("observed_on").asText(null);
        LocalDate fromField = tryParseObservedOnString(observedOn);
        if (fromField != null) {
            return fromField;
        }
        JsonNode details = observation.path("observed_on_details");
        if (details.hasNonNull("date")) {
            String d = details.get("date").asText(null);
            return tryParseIsoDateOnly(d);
        }
        return null;
    }

    private static LocalDate tryParseObservedOnString(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // e.g. full offset date-time
        }
        try {
            return OffsetDateTime.parse(s).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static LocalDate tryParseIsoDateOnly(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
