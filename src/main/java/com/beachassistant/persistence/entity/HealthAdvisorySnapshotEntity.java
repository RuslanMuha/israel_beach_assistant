package com.beachassistant.persistence.entity;

import com.beachassistant.common.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "health_advisory_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class HealthAdvisorySnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beach_id", nullable = false)
    private BeachEntity beach;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "captured_at", nullable = false)
    private ZonedDateTime capturedAt;

    @Column(name = "valid_from")
    private ZonedDateTime validFrom;

    @Column(name = "valid_to")
    private ZonedDateTime validTo;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "advisory_type")
    private String advisoryType;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    private String rawPayloadJson;
}
