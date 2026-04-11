package com.beachassistant.app.usecase;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.JellyfishSeverity;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.decision.freshness.FreshnessService;
import com.beachassistant.persistence.entity.BeachEntity;
import com.beachassistant.persistence.entity.JellyfishReportAggregateEntity;
import com.beachassistant.persistence.repository.JellyfishReportAggregateRepository;
import com.beachassistant.web.dto.JellyfishDto;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class JellyfishUseCase {

    private final BeachResolverUseCase beachResolver;
    private final JellyfishReportAggregateRepository jellyfishRepository;
    private final FreshnessService freshnessService;

    public JellyfishUseCase(BeachResolverUseCase beachResolver,
                              JellyfishReportAggregateRepository jellyfishRepository,
                              FreshnessService freshnessService) {
        this.beachResolver = beachResolver;
        this.jellyfishRepository = jellyfishRepository;
        this.freshnessService = freshnessService;
    }

    public JellyfishDto getJellyfish(String slugOrAlias) {
        BeachEntity beach = beachResolver.resolve(slugOrAlias);
        Optional<JellyfishReportAggregateEntity> opt =
                jellyfishRepository.findTopByBeachIdOrderByCapturedAtDesc(beach.getId());

        if (opt.isEmpty()) {
            return JellyfishDto.builder()
                    .beach(beach.getDisplayName())
                    .severityLevel(JellyfishSeverity.NONE)
                    .freshnessStatus(FreshnessStatus.EXPIRED)
                    .build();
        }

        JellyfishReportAggregateEntity entity = opt.get();
        FreshnessStatus freshness = freshnessService.classify(entity.getCapturedAt(), SourceType.JELLYFISH);

        return JellyfishDto.builder()
                .beach(beach.getDisplayName())
                .severityLevel(entity.getSeverityLevel())
                .confidenceLevel(entity.getConfidenceLevel())
                .reportCount(entity.getReportCount())
                .windowStart(entity.getWindowStart())
                .windowEnd(entity.getWindowEnd())
                .freshnessStatus(freshness)
                .capturedAt(entity.getCapturedAt())
                .build();
    }
}
