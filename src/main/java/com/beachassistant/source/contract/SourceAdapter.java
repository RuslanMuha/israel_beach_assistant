package com.beachassistant.source.contract;

import com.beachassistant.common.enums.SourceType;

import java.time.Duration;

/**
 * Contract for any data source the ingestion pipeline can poll. Existing adapters only need to
 * implement {@link #sourceType()} + {@link #fetch(SourceRequest)}; new adapters should also
 * override {@link #descriptor()} to feed the descriptor-driven scheduler and admin tooling.
 */
public interface SourceAdapter<T> {

    SourceType sourceType();

    FetchResult<T> fetch(SourceRequest request);

    /**
     * Exposes declarative metadata about this adapter. Default implementation derives a descriptor
     * from {@link #sourceType()} with a conservative 1h cadence so legacy adapters stay wired.
     */
    default SourceDescriptor descriptor() {
        return SourceDescriptor.legacy(sourceType(), Duration.ofHours(1));
    }
}
