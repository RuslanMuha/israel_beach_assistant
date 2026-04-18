package com.beachassistant.source.camera;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * {@code beach.providers.camera-snapshot.*} — stills capture configuration.
 *
 * <p>When {@link #isFfmpegEnabled()} is true the adapter shells out to {@code ffmpeg} to grab a
 * still from an HLS/RTSP stream; otherwise it falls back to HTTP GET on the
 * {@code snapshot_url}. Storage is either local disk or S3-compatible object storage.</p>
 */
@Component
@ConfigurationProperties(prefix = "beach.providers.camera-snapshot")
@Getter
@Setter
public class CameraSnapshotProperties {
    private boolean enabled = false;
    private Duration cadence = Duration.ofMinutes(15);
    private boolean ffmpegEnabled = false;
    private String ffmpegBinary = "ffmpeg";
    private Duration ffmpegTimeout = Duration.ofSeconds(10);

    private Storage storage = new Storage();

    @Getter
    @Setter
    public static class Storage {
        /** {@code LOCAL} or {@code S3}. */
        private String mode = "LOCAL";
        /** Directory for LOCAL mode. */
        private String localDir = "/var/lib/beach-assistant/snapshots";
        /** Bucket name for S3 mode. */
        private String s3Bucket;
        /** S3 key prefix. */
        private String s3Prefix = "snapshots/";
        /** Public-facing URL template, e.g. {@code https://cdn.example.com/{path}}. */
        private String publicUrlTemplate;
    }
}
