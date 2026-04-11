package com.beachassistant.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class CameraSnapshotDto {

    private final String beach;
    private final ZonedDateTime capturedAt;
    private final String storageUrl;
    private final Integer width;
    private final Integer height;
}
