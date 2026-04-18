package com.beachassistant.source.lifeguard;

import lombok.Builder;
import lombok.Value;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * External-feed record for lifeguard schedule updates. Adapters for municipal or union feeds
 * emit these; the ingestion layer persists them as {@code OVERRIDE} rows in
 * {@code lifeguard_schedule}.
 */
@Value
@Builder
public class LifeguardScheduleRecord {
    String beachSlug;
    /** ISO day-of-week 1–7 (Mon=1); {@code null} means every day. */
    Integer dayOfWeek;
    LocalTime openTime;
    LocalTime closeTime;
    /** Free-text note (e.g., reason for override). */
    String note;
    ZonedDateTime capturedAt;
    String rawPayloadJson;
}
