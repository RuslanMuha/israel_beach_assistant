package com.beachassistant.persistence.entity;

import com.beachassistant.common.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "beach_source_mapping",
        uniqueConstraints = @UniqueConstraint(columnNames = {"beach_id", "source_type"}))
@Getter
@Setter
@NoArgsConstructor
public class BeachSourceMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "beach_id", nullable = false)
    private BeachEntity beach;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "external_key")
    private String externalKey;

    @Column(name = "external_name")
    private String externalName;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = true;

    @Column(name = "metadata_json", columnDefinition = "TEXT")
    private String metadataJson;
}
