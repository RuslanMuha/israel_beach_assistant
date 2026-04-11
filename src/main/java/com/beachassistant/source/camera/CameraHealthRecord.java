package com.beachassistant.source.camera;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class CameraHealthRecord {

    private final String beachSlug;
    private final ZonedDateTime checkedAt;
    private final boolean healthy;
    private final String healthStatus;
}
