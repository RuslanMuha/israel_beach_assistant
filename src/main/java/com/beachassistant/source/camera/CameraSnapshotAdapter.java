package com.beachassistant.source.camera;

import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.CameraEndpointEntity;
import com.beachassistant.persistence.entity.CameraSnapshotEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.CameraEndpointRepository;
import com.beachassistant.persistence.repository.CameraSnapshotRepository;
import com.beachassistant.source.contract.FetchResult;
import com.beachassistant.source.contract.SourceAdapter;
import com.beachassistant.source.contract.SourceDescriptor;
import com.beachassistant.source.contract.SourceRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Produces periodic stills from beach cameras for use in Telegram {@code /cam} replies and
 * historical freshness signals. Two capture modes:
 * <ul>
 *     <li>HTTP snapshot URL (default): GET the static {@code camera_endpoint.snapshot_url}.</li>
 *     <li>{@code ffmpeg} live-stream grab: enabled via
 *         {@code beach.providers.camera-snapshot.ffmpeg-enabled=true}.</li>
 * </ul>
 * Stored bytes are handed to {@link CameraSnapshotStorage}, then a {@link CameraSnapshotEntity}
 * row is persisted with the resulting URL.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "beach.providers.camera-snapshot", name = "enabled", havingValue = "true")
public class CameraSnapshotAdapter implements SourceAdapter<CameraSnapshotRecord> {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final CameraSnapshotProperties props;
    private final BeachRepository beaches;
    private final CameraEndpointRepository cameraEndpoints;
    private final CameraSnapshotRepository snapshots;
    private final CameraSnapshotStorage storage;
    private final Clock clock;

    public CameraSnapshotAdapter(CameraSnapshotProperties props,
                                 BeachRepository beaches,
                                 CameraEndpointRepository cameraEndpoints,
                                 CameraSnapshotRepository snapshots,
                                 CameraSnapshotStorage storage,
                                 Clock clock) {
        this.props = props;
        this.beaches = beaches;
        this.cameraEndpoints = cameraEndpoints;
        this.snapshots = snapshots;
        this.storage = storage;
        this.clock = clock;
    }

    @Override
    public SourceType sourceType() {
        return SourceType.CAMERA_SNAPSHOT;
    }

    @Override
    public SourceDescriptor descriptor() {
        return new SourceDescriptor(
                "camera-snapshot",
                sourceType(),
                "Camera snapshot capture",
                props.getCadence(),
                "CAMERA_HEALTH"
        );
    }

    @Override
    public FetchResult<CameraSnapshotRecord> fetch(SourceRequest request) {
        BeachEntity beach = beaches.findBySlugAndActiveTrue(request.getBeachSlug()).orElse(null);
        if (beach == null) {
            return FetchResult.failure(sourceType(), "Unknown beach: " + request.getBeachSlug());
        }
        Optional<CameraEndpointEntity> maybeCamera =
                cameraEndpoints.findTopByBeachIdAndActiveTrueOrderByIdAsc(beach.getId());
        if (maybeCamera.isEmpty()) {
            return FetchResult.failure(sourceType(), "No active camera for beach: " + request.getBeachSlug());
        }
        CameraEndpointEntity camera = maybeCamera.get();
        try {
            byte[] bytes = props.isFfmpegEnabled() && camera.getLiveUrl() != null
                    ? grabWithFfmpeg(camera.getLiveUrl())
                    : fetchHttp(camera.getSnapshotUrl());
            if (bytes == null || bytes.length == 0) {
                return FetchResult.failure(sourceType(), "Empty snapshot bytes");
            }
            String url = storage.store(beach.getSlug(), bytes);
            ZonedDateTime now = ZonedDateTime.now(clock.withZone(ISRAEL));
            CameraSnapshotEntity row = new CameraSnapshotEntity();
            row.setCamera(camera);
            row.setCapturedAt(now);
            row.setStorageUrl(url);
            row.setAnalysisStatus("RAW");
            snapshots.save(row);

            CameraSnapshotRecord record = CameraSnapshotRecord.builder()
                    .beachSlug(beach.getSlug())
                    .cameraId(camera.getId())
                    .capturedAt(now)
                    .storageUrl(url)
                    .build();
            return FetchResult.success(sourceType(), List.of(record));
        } catch (Exception e) {
            log.warn("Camera snapshot failed for beach={}: {}", beach.getSlug(), e.getMessage());
            return FetchResult.failure(sourceType(), e.getMessage());
        }
    }

    private byte[] fetchHttp(String url) throws Exception {
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("snapshot_url is blank");
        }
        HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(12_000);
        conn.setRequestProperty("User-Agent", "BeachAssistant/camera-snapshot");
        try (InputStream in = conn.getInputStream();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private byte[] grabWithFfmpeg(String liveUrl) throws Exception {
        Path tmp = Files.createTempFile("beach-snap-", ".jpg");
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.getFfmpegBinary(),
                    "-y",
                    "-rtsp_transport", "tcp",
                    "-i", liveUrl,
                    "-vframes", "1",
                    "-q:v", "3",
                    tmp.toAbsolutePath().toString()
            ).redirectErrorStream(true);
            Process p = pb.start();
            boolean finished = p.waitFor(props.getFfmpegTimeout().toMillis(),
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new IllegalStateException("ffmpeg timed out after "
                        + props.getFfmpegTimeout().toMillis() + "ms");
            }
            if (p.exitValue() != 0) {
                throw new IllegalStateException("ffmpeg exited with code " + p.exitValue());
            }
            return Files.readAllBytes(tmp);
        } finally {
            try { Files.deleteIfExists(tmp); } catch (Exception ignored) {}
        }
    }
}
