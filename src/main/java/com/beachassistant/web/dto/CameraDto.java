package com.beachassistant.web.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.ZonedDateTime;

@Getter
@Builder
public class CameraDto {

    private final String beach;
    private final String providerName;
    private final String liveUrl;
    private final boolean isActive;
    private final String healthStatus;
    private final ZonedDateTime lastCheckedAt;
}
