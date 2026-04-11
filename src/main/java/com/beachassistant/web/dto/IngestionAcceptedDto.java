package com.beachassistant.web.dto;

import com.beachassistant.common.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

/**
 * Response for async admin-triggered ingestion ({@code 202 Accepted}).
 */
@Getter
@Builder
public class IngestionAcceptedDto {

    private final Long runId;
    private final SourceType sourceType;
    private final String status;
}
