package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "camera_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class CameraSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "camera_id", nullable = false)
    private CameraEndpointEntity camera;

    @Column(name = "captured_at", nullable = false)
    private ZonedDateTime capturedAt;

    @Column(name = "storage_url")
    private String storageUrl;

    private Integer width;
    private Integer height;

    @Column(name = "analysis_status")
    private String analysisStatus;

    @Column(name = "crowd_level")
    private String crowdLevel;

    @Column(name = "visibility_level")
    private String visibilityLevel;
}
