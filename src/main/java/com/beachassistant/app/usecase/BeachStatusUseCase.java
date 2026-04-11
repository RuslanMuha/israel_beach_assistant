package com.beachassistant.app.usecase;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.JellyfishSeverity;
import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.common.enums.SeaRiskLevel;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.decision.engine.DecisionEngine;
import com.beachassistant.decision.freshness.FreshnessService;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachSignals;
import com.beachassistant.persistence.entity.*;
import com.beachassistant.persistence.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class BeachStatusUseCase {

    private static final ZoneId ISRAEL = ZoneId.of("Asia/Jerusalem");

    private final BeachRepository beachRepository;
    private final SeaConditionSnapshotRepository seaRepo;
    private final HealthAdvisorySnapshotRepository advisoryRepo;
    private final LifeguardScheduleRepository scheduleRepo;
    private final JellyfishReportAggregateRepository jellyfishRepo;
    private final FreshnessService freshnessService;
    private final DecisionEngine decisionEngine;
    private final BeachResolverUseCase beachResolver;
    private final BeachMetrics metrics;

    public BeachStatusUseCase(BeachRepository beachRepository,
                               SeaConditionSnapshotRepository seaRepo,
                               HealthAdvisorySnapshotRepository advisoryRepo,
                               LifeguardScheduleRepository scheduleRepo,
                               JellyfishReportAggregateRepository jellyfishRepo,
                               FreshnessService freshnessService,
                               DecisionEngine decisionEngine,
                               BeachResolverUseCase beachResolver,
                               BeachMetrics metrics) {
        this.beachRepository = beachRepository;
        this.seaRepo = seaRepo;
        this.advisoryRepo = advisoryRepo;
        this.scheduleRepo = scheduleRepo;
        this.jellyfishRepo = jellyfishRepo;
        this.freshnessService = freshnessService;
        this.decisionEngine = decisionEngine;
        this.beachResolver = beachResolver;
        this.metrics = metrics;
    }

    @Cacheable(value = "beachStatus", key = "#slugOrAlias")
    public BeachDecision getStatus(String slugOrAlias) {
        BeachEntity beach = beachResolver.resolve(slugOrAlias);
        return evaluate(beach);
    }

    public BeachDecision evaluate(BeachEntity beach) {
        Long beachId = beach.getId();
        Map<SourceType, FreshnessStatus> freshness = new EnumMap<>(SourceType.class);
        Map<SourceType, String> failures = new EnumMap<>(SourceType.class);

        // Sea conditions
        Optional<SeaConditionSnapshotEntity> seaOpt = seaRepo.findTopByBeachIdOrderByCapturedAtDesc(beachId);
        SeaConditionSnapshotEntity sea = seaOpt.orElse(null);
        ZonedDateTime seaCapturedAt = sea != null ? sea.getCapturedAt() : null;
        freshness.put(SourceType.SEA_FORECAST,
                freshnessService.classify(seaCapturedAt, SourceType.SEA_FORECAST));
        if (sea == null) {
            failures.put(SourceType.SEA_FORECAST, "no snapshot in db");
        }

        // Health advisory (look for the most recent, whether active or not)
        Optional<HealthAdvisorySnapshotEntity> advisoryOpt =
                advisoryRepo.findTopByBeachIdOrderByCapturedAtDesc(beachId);
        HealthAdvisorySnapshotEntity advisory = advisoryOpt.orElse(null);
        ZonedDateTime advisoryCapturedAt = advisory != null ? advisory.getCapturedAt() : null;
        freshness.put(SourceType.HEALTH_ADVISORY,
                freshnessService.classify(advisoryCapturedAt, SourceType.HEALTH_ADVISORY));
        if (advisory == null) {
            failures.put(SourceType.HEALTH_ADVISORY, "no snapshot in db");
        }

        // Lifeguard schedule
        LocalDate today = LocalDate.now(ISRAEL);
        int dayOfWeek = today.getDayOfWeek().getValue();
        List<LifeguardScheduleEntity> schedules = scheduleRepo.findActiveSchedulesForDate(beachId, today);
        LifeguardScheduleEntity schedule = schedules.stream()
                .filter(s -> s.getDayOfWeek() == null || s.getDayOfWeek() == dayOfWeek)
                .findFirst()
                .orElse(null);

        boolean lifeguardOnDuty = false;
        String openTimeStr = null;
        String closeTimeStr = null;
        ZonedDateTime scheduleCapturedAt = null;

        if (schedule != null) {
            LocalTime now = LocalTime.now(ISRAEL);
            LocalTime open = schedule.getOpenTime();
            LocalTime close = schedule.getCloseTime();
            lifeguardOnDuty = open != null && close != null && !now.isBefore(open) && now.isBefore(close);
            openTimeStr = open != null ? open.toString() : null;
            closeTimeStr = close != null ? close.toString() : null;
            scheduleCapturedAt = schedule.getCapturedAt();
        }
        freshness.put(SourceType.LIFEGUARD_SCHEDULE,
                freshnessService.classify(scheduleCapturedAt, SourceType.LIFEGUARD_SCHEDULE));

        // Jellyfish
        Optional<JellyfishReportAggregateEntity> jellyfishOpt =
                jellyfishRepo.findTopByBeachIdOrderByCapturedAtDesc(beachId);
        JellyfishReportAggregateEntity jellyfish = jellyfishOpt.orElse(null);
        ZonedDateTime jellyfishCapturedAt = jellyfish != null ? jellyfish.getCapturedAt() : null;
        freshness.put(SourceType.JELLYFISH,
                freshnessService.classify(jellyfishCapturedAt, SourceType.JELLYFISH));

        Map<SourceType, ZonedDateTime> sourceCapturedAt = new EnumMap<>(SourceType.class);
        if (seaCapturedAt != null) {
            sourceCapturedAt.put(SourceType.SEA_FORECAST, seaCapturedAt);
        }
        if (advisoryCapturedAt != null) {
            sourceCapturedAt.put(SourceType.HEALTH_ADVISORY, advisoryCapturedAt);
        }
        if (scheduleCapturedAt != null) {
            sourceCapturedAt.put(SourceType.LIFEGUARD_SCHEDULE, scheduleCapturedAt);
        }
        if (jellyfishCapturedAt != null) {
            sourceCapturedAt.put(SourceType.JELLYFISH, jellyfishCapturedAt);
        }

        BeachSignals signals = BeachSignals.builder()
                .beachSlug(beach.getSlug())
                .beachDisplayName(beach.getDisplayName())
                .city(beach.getCity().getName())
                .lifeguardScheduleKnown(schedule != null)
                .seaRiskLevel(sea != null ? sea.getSeaRiskLevel() : null)
                .waveHeightM(sea != null ? sea.getWaveHeightM() : null)
                .airTemperatureC(sea != null ? sea.getAirTemperatureC() : null)
                .relativeHumidityPct(sea != null ? sea.getRelativeHumidityPct() : null)
                .uvIndex(sea != null ? sea.getUvIndex() : null)
                .windSpeedMps(sea != null ? sea.getWindSpeedMps() : null)
                .windDirection(sea != null ? sea.getWindDirection() : null)
                .seaTemperatureC(sea != null ? sea.getSeaTemperatureC() : null)
                .seaCapturedAt(seaCapturedAt)
                .seaValidFrom(sea != null ? sea.getValidFrom() : null)
                .seaValidTo(sea != null ? sea.getValidTo() : null)
                .seaIntervalIsInferred(sea != null && sea.isIntervalIsInferred())
                .healthAdvisoryActive(advisory != null && advisory.isActive())
                .healthAdvisoryMessage(advisory != null ? advisory.getMessage() : null)
                .advisoryCapturedAt(advisoryCapturedAt)
                .lifeguardOnDuty(lifeguardOnDuty)
                .lifeguardOpenTime(openTimeStr)
                .lifeguardCloseTime(closeTimeStr)
                .lifeguardCapturedAt(scheduleCapturedAt)
                .jellyfishSeverity(jellyfish != null ? jellyfish.getSeverityLevel() : JellyfishSeverity.NONE)
                .jellyfishReportCount(jellyfish != null ? jellyfish.getReportCount() : 0)
                .jellyfishWindowStart(jellyfish != null ? jellyfish.getWindowStart() : null)
                .jellyfishWindowEnd(jellyfish != null ? jellyfish.getWindowEnd() : null)
                .jellyfishCapturedAt(jellyfishCapturedAt)
                .beachClosed(false)
                .fetchFailures(failures)
                .sourceFreshness(freshness)
                .sourceCapturedAt(sourceCapturedAt)
                .build();

        BeachDecision decision = decisionEngine.evaluate(signals);
        metrics.recordDecision(decision.getRecommendation());
        metrics.recordDecisionConfidence(decision.getConfidence());
        freshness.forEach(metrics::recordFreshnessStatus);
        return decision;
    }
}
