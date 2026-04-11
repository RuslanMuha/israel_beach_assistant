package com.beachassistant.app.usecase;

import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.common.exception.CameraUnavailableException;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.entity.CameraSnapshotEntity;
import com.beachassistant.persistence.repository.CameraEndpointRepository;
import com.beachassistant.persistence.repository.CameraSnapshotRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
public class CameraUseCase {

    private final CameraEndpointRepository cameraEndpointRepository;
    private final CameraSnapshotRepository cameraSnapshotRepository;
    private final BeachResolverUseCase beachResolver;

    public CameraUseCase(CameraEndpointRepository cameraEndpointRepository,
                          CameraSnapshotRepository cameraSnapshotRepository,
                          BeachResolverUseCase beachResolver) {
        this.cameraEndpointRepository = cameraEndpointRepository;
        this.cameraSnapshotRepository = cameraSnapshotRepository;
        this.beachResolver = beachResolver;
    }

    public CameraEndpointEntity getActiveCamera(String slugOrAlias) {
        BeachEntity beach = beachResolver.resolve(slugOrAlias);
        return cameraEndpointRepository.findTopByBeachIdAndActiveTrueOrderByIdAsc(beach.getId())
                .orElseThrow(() -> new CameraUnavailableException(beach.getSlug()));
    }

    public Optional<CameraSnapshotEntity> getLatestSnapshot(String slugOrAlias) {
        CameraEndpointEntity camera = getActiveCamera(slugOrAlias);
        return cameraSnapshotRepository.findTopByCameraIdOrderByCapturedAtDesc(camera.getId());
    }
}
