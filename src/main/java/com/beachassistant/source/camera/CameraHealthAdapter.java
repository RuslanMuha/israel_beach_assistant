package com.beachassistant.source.camera;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.CameraEndpointRepository;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
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
    private final WebClient webClient;

    public CameraHealthAdapter(BeachProvidersProperties props,
                               BeachRepository beachRepository,
                               CameraEndpointRepository cameraEndpointRepository,
                               WebClient.Builder webClientBuilder) {
        this.props = props;
        this.beachRepository = beachRepository;
        this.cameraEndpointRepository = cameraEndpointRepository;
        this.webClient = webClientBuilder.build();
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
            Duration timeout = Duration.ofSeconds(props.getHttpTimeoutSeconds());
            boolean ok;
            try {
                webClient.head()
                        .uri(cam.getLiveUrl())
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(timeout)
                        .block(timeout.plusSeconds(2));
                ok = true;
            } catch (Exception e) {
                log.debug("Camera HEAD failed: {}", e.getMessage());
                ok = false;
            }
            CameraHealthRecord record = CameraHealthRecord.builder()
                    .beachSlug(request.getBeachSlug())
                    .checkedAt(ZonedDateTime.now())
                    .healthy(ok)
                    .healthStatus(ok ? "OK" : "UNREACHABLE")
                    .build();
            return FetchResult.success(sourceType(), List.of(record));
        } catch (Exception e) {
            log.error("Camera health fetch failed for beach={}: {}", request.getBeachSlug(), e.getMessage());
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
