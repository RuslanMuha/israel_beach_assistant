package com.beachassistant.web.controller;

import com.beachassistant.app.usecase.IngestionUseCase;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.persistence.entity.IngestionRunEntity;
import com.beachassistant.persistence.repository.IngestionRunRepository;
import com.beachassistant.web.dto.IngestionResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

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

        IngestionRunEntity run = ingestionUseCase.ingest(type);
        return ResponseEntity.ok(toDto(run));
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
