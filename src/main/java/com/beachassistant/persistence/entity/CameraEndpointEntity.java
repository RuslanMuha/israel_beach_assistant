package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "camera_endpoint")
@Getter
@Setter
@NoArgsConstructor
public class CameraEndpointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beach_id", nullable = false)
    private BeachEntity beach;

    @Column(name = "provider_name")
    private String providerName;

    @Column(name = "live_url")
    private String liveUrl;

    @Column(name = "snapshot_url")
    private String snapshotUrl;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_checked_at")
    private ZonedDateTime lastCheckedAt;

    @Column(name = "health_status")
    private String healthStatus;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}
