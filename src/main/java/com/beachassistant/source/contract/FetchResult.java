package com.beachassistant.source.contract;

import com.beachassistant.common.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
public class FetchResult<T> {

    private final SourceType sourceType;
    private final ZonedDateTime fetchedAt;
    private final List<T> records;
    private final List<String> warnings;
    private final boolean success;
    private final String errorMessage;
    private final String rawResponseMeta;

    public static <T> FetchResult<T> success(SourceType sourceType, List<T> records) {
        return FetchResult.<T>builder()
                .sourceType(sourceType)
                .fetchedAt(ZonedDateTime.now())
                .records(records)
                .warnings(Collections.emptyList())
                .success(true)
                .build();
    }

    public static <T> FetchResult<T> failure(SourceType sourceType, String errorMessage) {
        return FetchResult.<T>builder()
                .sourceType(sourceType)
                .fetchedAt(ZonedDateTime.now())
                .records(Collections.emptyList())
                .warnings(Collections.emptyList())
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }
}
