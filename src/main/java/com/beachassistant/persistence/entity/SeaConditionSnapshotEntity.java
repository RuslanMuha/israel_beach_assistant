package com.beachassistant.persistence.entity;

import com.beachassistant.common.enums.SeaRiskLevel;
import com.beachassistant.common.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "sea_condition_snapshot")
@Getter
@Setter
@NoArgsConstructor
public class SeaConditionSnapshotEntity {

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

    @Column(name = "wave_height_m")
    private Double waveHeightM;

    @Enumerated(EnumType.STRING)
    @Column(name = "sea_risk_level")
    private SeaRiskLevel seaRiskLevel;

    @Column(name = "wind_speed_mps")
    private Double windSpeedMps;

    @Column(name = "wind_direction")
    private String windDirection;

    @Column(name = "sea_temperature_c")
    private Double seaTemperatureC;

    @Column(name = "air_temperature_c")
    private Double airTemperatureC;

    @Column(name = "relative_humidity_pct")
    private Double relativeHumidityPct;

    @Column(name = "uv_index")
    private Double uvIndex;

    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    private String rawPayloadJson;

    @Column(name = "source_confidence")
    private String sourceConfidence;

    @Column(name = "interval_is_inferred", nullable = false)
    private boolean intervalIsInferred = false;
}
