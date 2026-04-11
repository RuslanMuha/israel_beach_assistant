package com.beachassistant.web.controller;

import com.beachassistant.app.usecase.IngestionUseCase;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import com.beachassistant.persistence.repository.IngestionRunRepository;
import com.beachassistant.web.dto.IngestionAcceptedDto;
import com.beachassistant.web.dto.IngestionResultDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final IngestionUseCase ingestionUseCase;
    private final IngestionRunRepository ingestionRunRepository;

    public AdminController(IngestionUseCase ingestionUseCase,
                            IngestionRunRepository ingestionRunRepository) {
        this.ingestionUseCase = ingestionUseCase;
        this.ingestionRunRepository = ingestionRunRepository;
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
