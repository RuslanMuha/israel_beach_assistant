package com.beachassistant.web.controller;

import com.beachassistant.app.usecase.IngestionUseCase;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.ClosureSnapshotEntity;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import com.beachassistant.persistence.repository.BeachRepository;
import com.beachassistant.persistence.repository.ClosureSnapshotRepository;
import com.beachassistant.persistence.repository.IngestionRunRepository;
import com.beachassistant.web.dto.ClosureOverrideRequestDto;
import com.beachassistant.web.dto.IngestionAcceptedDto;
import com.beachassistant.web.dto.IngestionResultDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final IngestionUseCase ingestionUseCase;
    private final IngestionRunRepository ingestionRunRepository;
    private final ClosureSnapshotRepository closureRepository;
    private final BeachRepository beachRepository;
    private final Clock clock;

    public AdminController(IngestionUseCase ingestionUseCase,
                            IngestionRunRepository ingestionRunRepository,
                            ClosureSnapshotRepository closureRepository,
                            BeachRepository beachRepository,
                            Clock clock) {
        this.ingestionUseCase = ingestionUseCase;
        this.ingestionRunRepository = ingestionRunRepository;
        this.closureRepository = closureRepository;
        this.beachRepository = beachRepository;
        this.clock = clock;
    }

    /**
     * Starts ingestion asynchronously (returns {@code 202} with run id). Poll {@link #getRun(long)} or {@link #getLastRuns()}.
     * Returns {@code 409} when {@link com.beachassistant.scheduler.IngestionCycleGuard} blocks overlap for this source.
     */
    @PostMapping("/ingest/{sourceType}")
    public ResponseEntity<?> triggerIngest(@PathVariable String sourceType) {
        SourceType type;
        try {
            type = SourceType.valueOf(sourceType.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(java.util.Map.of("error", "INVALID_SOURCE_TYPE",
                            "message", "Unknown source type: " + sourceType));
        }

        Optional<IngestionRunEntity> started = ingestionUseCase.startIngestionAsync(type);
        if (started.isEmpty()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(java.util.Map.of(
                            "error", "INGESTION_OVERLAP",
                            "message", "An ingestion run for this source is already active"));
        }
        IngestionRunEntity run = started.get();
        IngestionAcceptedDto body = IngestionAcceptedDto.builder()
                .runId(run.getId())
                .sourceType(run.getSourceType())
                .status(run.getStatus())
                .build();
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/admin/runs/" + run.getId()))
                .body(body);
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<IngestionResultDto> getRun(@PathVariable long id) {
        return ingestionRunRepository.findById(id)
                .map(this::toDto)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/diagnostics")
    public List<IngestionResultDto> getLastRuns() {
        return Arrays.stream(SourceType.values())
                .map(t -> ingestionRunRepository.findTopBySourceTypeOrderByStartedAtDesc(t)
                        .map(this::toDto)
                        .orElse(IngestionResultDto.builder()
                                .sourceType(t)
                                .status("NEVER_RUN")
                                .build()))
                .toList();
    }

    /**
     * Records an admin closure override for a beach. Stored as a new {@link ClosureSnapshotEntity}
     * with {@code source=ADMIN_OVERRIDE}; the most recent snapshot wins when composing the status.
     */
    @PostMapping("/beaches/{slug}/closure-override")
    public ResponseEntity<?> setClosureOverride(@PathVariable String slug,
                                                @Valid @RequestBody ClosureOverrideRequestDto body) {
        Optional<BeachEntity> beach = beachRepository.findBySlugAndActiveTrue(slug);
        if (beach.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "BEACH_NOT_FOUND", "message", slug));
        }
        ZonedDateTime now = ZonedDateTime.now(clock);
        ClosureSnapshotEntity row = new ClosureSnapshotEntity();
        row.setBeachId(beach.get().getId());
        row.setClosed(body.isClosed());
        row.setReason(body.getReason());
        row.setSource("ADMIN_OVERRIDE");
        row.setEffectiveFrom(body.getEffectiveFrom() != null ? body.getEffectiveFrom() : now);
        row.setEffectiveUntil(body.getEffectiveUntil());
        row.setCapturedAt(now);
        row.setCreatedAt(now);
        row.setRawPayloadJson("{\"override\":true}");
        closureRepository.save(row);
        return ResponseEntity.ok(Map.of(
                "beachSlug", slug,
                "closed", row.isClosed(),
                "reason", row.getReason() != null ? row.getReason() : "",
                "effectiveFrom", row.getEffectiveFrom().toString(),
                "effectiveUntil", row.getEffectiveUntil() != null ? row.getEffectiveUntil().toString() : ""
        ));
    }

    @GetMapping("/beaches/{slug}/closure")
    public ResponseEntity<?> getClosure(@PathVariable String slug) {
        Optional<BeachEntity> beach = beachRepository.findBySlugAndActiveTrue(slug);
        if (beach.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return closureRepository.findFirstByBeachIdOrderByCapturedAtDesc(beach.get().getId())
                .<ResponseEntity<?>>map(c -> ResponseEntity.ok(Map.of(
                        "beachSlug", slug,
                        "closed", c.isClosed(),
                        "reason", c.getReason() != null ? c.getReason() : "",
                        "source", c.getSource(),
                        "capturedAt", c.getCapturedAt().toString()
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of(
                        "beachSlug", slug,
                        "closed", false,
                        "source", "NONE"
                )));
    }

    private IngestionResultDto toDto(IngestionRunEntity run) {
        return IngestionResultDto.builder()
                .runId(run.getId())
                .sourceType(run.getSourceType())
                .startedAt(run.getStartedAt())
                .finishedAt(run.getFinishedAt())
                .status(run.getStatus())
                .recordsFetched(run.getRecordsFetched())
                .recordsSaved(run.getRecordsSaved())
                .errorSummary(run.getErrorSummary())
                .build();
    }
}
