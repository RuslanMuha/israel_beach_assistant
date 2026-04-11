package com.beachassistant.scheduler;

import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.repository.CameraEndpointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Component
@Profile("!test")
public class CameraHealthScheduler {

    private final CameraEndpointRepository cameraEndpointRepository;
    private final WebClient webClient;

    public CameraHealthScheduler(CameraEndpointRepository cameraEndpointRepository,
                                   WebClient.Builder builder) {
        this.cameraEndpointRepository = cameraEndpointRepository;
        this.webClient = builder.build();
    }

    @Scheduled(fixedRateString = "${beach.scheduler.camera-health-rate-ms:900000}")
    @Transactional
    public void checkAllCameras() {
        List<CameraEndpointEntity> cameras = cameraEndpointRepository.findAll();
        for (CameraEndpointEntity camera : cameras) {
            if (!camera.isActive() || camera.getLiveUrl() == null) {
                continue;
            }
            try {
                webClient.head()
                        .uri(camera.getLiveUrl())
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(Duration.ofSeconds(5))
                        .block();
                camera.setHealthStatus("OK");
                log.debug("Camera health OK: beach={}", camera.getBeach().getSlug());
            } catch (Exception e) {
                camera.setHealthStatus("UNREACHABLE");
                log.warn("Camera health check failed: beach={} url={}", camera.getBeach().getSlug(), camera.getLiveUrl());
            }
            camera.setLastCheckedAt(ZonedDateTime.now());
            cameraEndpointRepository.save(camera);
        }
    }
}
