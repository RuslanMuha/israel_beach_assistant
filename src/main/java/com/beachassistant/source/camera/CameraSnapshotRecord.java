package com.beachassistant.source.camera;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Value
@Builder
public class CameraSnapshotRecord {
    String beachSlug;
    Long cameraId;
    ZonedDateTime capturedAt;
    String storageUrl;
    Integer width;
    Integer height;
}
