package com.beachassistant.persistence.entity;

import com.beachassistant.common.enums.SourceType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "ingestion_run")
@Getter
@Setter
@NoArgsConstructor
public class IngestionRunEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "started_at", nullable = false)
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    @Column(nullable = false)
    private String status;

    @Column(name = "records_fetched")
    private Integer recordsFetched;

    @Column(name = "records_saved")
    private Integer recordsSaved;

    @Column(name = "error_summary", columnDefinition = "TEXT")
    private String errorSummary;
}
