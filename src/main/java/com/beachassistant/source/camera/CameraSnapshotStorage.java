package com.beachassistant.source.camera;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes captured stills to local disk or an S3-compatible object store and returns the URL
 * consumers should use. S3 is a seam: the concrete SDK call lives behind {@link #uploadS3}
 * so the adapter compiles without aws-sdk on the classpath; implementations can add the
 * dependency + logic when object storage is rolled out.
 */
@Slf4j
@Component
public class CameraSnapshotStorage {

    private final CameraSnapshotProperties props;

    public CameraSnapshotStorage(CameraSnapshotProperties props) {
        this.props = props;
    }

    /**
     * Persists {@code bytes} under a filename derived from {@code beachSlug} and returns the
     * URL to publish to consumers (Telegram SendPhoto, DB {@code storage_url}).
     */
    public String store(String beachSlug, byte[] bytes) throws IOException {
        String filename = beachSlug + "-" + System.currentTimeMillis() + ".jpg";
        CameraSnapshotProperties.Storage storage = props.getStorage();
        return switch (storage.getMode() == null ? "LOCAL" : storage.getMode().toUpperCase()) {
            case "S3" -> uploadS3(storage, filename, bytes);
            default -> writeLocal(storage, filename, bytes);
        };
    }

    private String writeLocal(CameraSnapshotProperties.Storage storage, String filename, byte[] bytes)
            throws IOException {
        Path dir = Paths.get(storage.getLocalDir());
        Files.createDirectories(dir);
        Path file = dir.resolve(filename);
        Files.write(file, bytes);
        if (storage.getPublicUrlTemplate() != null && storage.getPublicUrlTemplate().contains("{path}")) {
            return storage.getPublicUrlTemplate().replace("{path}", filename);
        }
        return file.toUri().toString();
    }

    /** Stub S3 upload — wire concrete SDK integration when object storage is enabled. */
    private String uploadS3(CameraSnapshotProperties.Storage storage, String filename, byte[] bytes) {
        log.warn("S3 snapshot upload requested but not wired; falling back to memory-only URL");
        if (storage.getPublicUrlTemplate() != null && storage.getPublicUrlTemplate().contains("{path}")) {
            return storage.getPublicUrlTemplate()
                    .replace("{path}", storage.getS3Prefix() + filename);
        }
        return "s3://" + storage.getS3Bucket() + "/" + storage.getS3Prefix() + filename;
    }
}
