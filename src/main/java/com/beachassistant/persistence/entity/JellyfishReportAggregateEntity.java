package com.beachassistant.persistence.entity;

import com.beachassistant.common.enums.JellyfishSeverity;
import com.beachassistant.common.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "jellyfish_report_aggregate")
@Getter
@Setter
@NoArgsConstructor
public class JellyfishReportAggregateEntity {

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

    @Column(name = "window_start")
    private ZonedDateTime windowStart;

    @Column(name = "window_end")
    private ZonedDateTime windowEnd;

    @Column(name = "report_count")
    private Integer reportCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity_level")
    private JellyfishSeverity severityLevel;

    @Column(name = "confidence_level")
    private String confidenceLevel;

    @Column(name = "raw_payload_json", columnDefinition = "TEXT")
    private String rawPayloadJson;
}
