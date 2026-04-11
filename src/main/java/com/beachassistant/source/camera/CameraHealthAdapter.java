package com.beachassistant.source.camera;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.integration.IntegrationSourceKey;
import com.beachassistant.integration.http.OutboundHttpService;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.CameraEndpointRepository;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Reachability check for the configured live camera page (HTTP HEAD), same idea as {@link com.beachassistant.scheduler.CameraHealthScheduler}.
 */
@Slf4j
@Component
public class CameraHealthAdapter implements SourceAdapter<CameraHealthRecord> {

    private final BeachProvidersProperties props;
    private final BeachRepository beachRepository;
    private final CameraEndpointRepository cameraEndpointRepository;
    private final OutboundHttpService outboundHttpService;

    public CameraHealthAdapter(BeachProvidersProperties props,
                               BeachRepository beachRepository,
                               CameraEndpointRepository cameraEndpointRepository,
                               OutboundHttpService outboundHttpService) {
        this.props = props;
        this.beachRepository = beachRepository;
        this.cameraEndpointRepository = cameraEndpointRepository;
        this.outboundHttpService = outboundHttpService;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.CAMERA_HEALTH;
    }

    @Override
    public FetchResult<CameraHealthRecord> fetch(SourceRequest request) {
        if (props.isStub()) {
            return stubFetch(request);
        }
        try {
            BeachEntity beach = beachRepository.findBySlugAndActiveTrue(request.getBeachSlug())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown beach slug: " + request.getBeachSlug()));
            CameraEndpointEntity cam = cameraEndpointRepository
                    .findTopByBeachIdAndActiveTrueOrderByIdAsc(beach.getId())
                    .orElse(null);
            if (cam == null || cam.getLiveUrl() == null || cam.getLiveUrl().isBlank()) {
                CameraHealthRecord record = CameraHealthRecord.builder()
                        .beachSlug(request.getBeachSlug())
                        .checkedAt(ZonedDateTime.now())
                        .healthy(false)
                        .healthStatus("NO_ENDPOINT")
                        .build();
                return FetchResult.success(sourceType(), List.of(record));
            }
            URI uri;
            try {
                uri = URI.create(cam.getLiveUrl());
            } catch (IllegalArgumentException e) {
                return FetchResult.failure(sourceType(), "Invalid camera URL: " + cam.getLiveUrl());
            }
            var head = outboundHttpService.head(IntegrationSourceKey.CAMERA, uri, "camera_head");
            boolean ok = head.reachable();
            CameraHealthRecord record = CameraHealthRecord.builder()
                    .beachSlug(request.getBeachSlug())
                    .checkedAt(ZonedDateTime.now())
                    .healthy(ok)
                    .healthStatus(ok ? "OK" : "UNREACHABLE")
                    .build();
            return FetchResult.success(sourceType(), List.of(record));
        } catch (Exception e) {
            log.warn("Camera health fetch failed for beach={}: {}", request.getBeachSlug(), e.getMessage());
            return FetchResult.failure(sourceType(), e.getMessage());
        }
    }

    private FetchResult<CameraHealthRecord> stubFetch(SourceRequest request) {
        CameraHealthRecord record = CameraHealthRecord.builder()
                .beachSlug(request.getBeachSlug())
                .checkedAt(ZonedDateTime.now())
                .healthy(true)
                .healthStatus("OK")
                .build();
        return FetchResult.success(sourceType(), List.of(record));
    }
}
