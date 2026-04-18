package com.beachassistant.source._template;

import java.time.ZonedDateTime;

/**
 * Immutable record returned from {@link TemplateAdapter}. Shape it to match what the adapter
 * produces and what the ingestion pipeline needs to persist.
 */
public record TemplateRecord(
        String externalId,
        ZonedDateTime observedAt,
        String rawPayload
) {
}
