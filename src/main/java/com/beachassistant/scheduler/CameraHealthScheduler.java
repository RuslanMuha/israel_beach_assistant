package com.beachassistant.scheduler;

import com.beachassistant.integration.IntegrationSourceKey;
import com.beachassistant.integration.http.BeachIntegrationProperties;
import com.beachassistant.integration.http.OutboundHttpService;
import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.repository.CameraEndpointRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Component
@Profile("!test")
public class CameraHealthScheduler {

    private final CameraEndpointRepository cameraEndpointRepository;
    private final OutboundHttpService outboundHttpService;
    private final BeachIntegrationProperties integrationProperties;
    private final ReentrantLock runLock = new ReentrantLock();

    public CameraHealthScheduler(CameraEndpointRepository cameraEndpointRepository,
                                   OutboundHttpService outboundHttpService,
                                   BeachIntegrationProperties integrationProperties) {
        this.cameraEndpointRepository = cameraEndpointRepository;
        this.outboundHttpService = outboundHttpService;
        this.integrationProperties = integrationProperties;
    }

    @Scheduled(
            initialDelayString = "${beach.integration.scheduler.initial-delay-ms:90000}",
            fixedDelayString = "${beach.scheduler.camera-health-rate-ms:900000}"
    )
    @Transactional
    public void checkAllCameras() {
        boolean locked = false;
        if (integrationProperties.getScheduler().isSkipIfOverlap()) {
            if (!runLock.tryLock()) {
                log.info("Camera health check skipped (overlap)");
                return;
            }
            locked = true;
        }
        try {
            List<CameraEndpointEntity> cameras = cameraEndpointRepository.findAll();
            for (CameraEndpointEntity camera : cameras) {
                if (!camera.isActive() || camera.getLiveUrl() == null) {
                    continue;
                }
                try {
                    URI uri = URI.create(camera.getLiveUrl());
                    var head = outboundHttpService.head(IntegrationSourceKey.CAMERA, uri, "camera_head");
                    if (head.reachable()) {
                        camera.setHealthStatus("OK");
                        log.debug("Camera health OK: beach={}", camera.getBeach().getSlug());
                    } else {
                        camera.setHealthStatus("UNREACHABLE");
                        log.warn("Camera health check failed: beach={} url={} status={}",
                                camera.getBeach().getSlug(), camera.getLiveUrl(), head.statusCode());
                    }
                } catch (IllegalArgumentException e) {
                    camera.setHealthStatus("UNREACHABLE");
                    log.warn("Camera health invalid URL: beach={} url={}", camera.getBeach().getSlug(), camera.getLiveUrl());
                }
                camera.setLastCheckedAt(ZonedDateTime.now());
                cameraEndpointRepository.save(camera);
            }
        } finally {
            if (locked) {
                runLock.unlock();
            }
        }
    }
}
