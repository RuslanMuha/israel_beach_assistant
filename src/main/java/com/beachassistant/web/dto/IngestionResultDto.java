package com.beachassistant.web.dto;

import com.beachassistant.common.enums.SourceType;
import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class IngestionResultDto {

    private final SourceType sourceType;
    private final ZonedDateTime startedAt;
    private final ZonedDateTime finishedAt;
    private final String status;
    private final Integer recordsFetched;
    private final Integer recordsSaved;
    private final String errorSummary;
}
