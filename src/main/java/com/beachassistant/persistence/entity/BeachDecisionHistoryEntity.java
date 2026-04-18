package com.beachassistant.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "beach_decision_history")
@Getter
@Setter
@NoArgsConstructor
public class BeachDecisionHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "beach_id", nullable = false)
    private Long beachId;

    @Column(nullable = false, length = 32)
    private String recommendation;

    @Column(name = "reason_codes", nullable = false, columnDefinition = "TEXT")
    private String reasonCodes;

    @Column(name = "freshness_bucket", nullable = false, length = 16)
    private String freshnessBucket;

    @Column(name = "signature_hash", nullable = false, length = 64)
    private String signatureHash;

    @Column(name = "generated_at", nullable = false)
    private ZonedDateTime generatedAt;
}
