package com.beachassistant.decision.engine;

import com.beachassistant.common.enums.*;
import com.beachassistant.common.util.TimeUtil;
import com.beachassistant.decision.freshness.FreshnessService;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachSignals;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DecisionEngine {

    private final FreshnessService freshnessService;

    public DecisionEngine(FreshnessService freshnessService) {
        this.freshnessService = freshnessService;
    }

    public BeachDecision evaluate(BeachSignals signals) {
        List<ReasonCode> reasons = new ArrayList<>();
        List<SourceType> missing = new ArrayList<>(signals.getFetchFailures().keySet());

        // Determine overall freshness
        FreshnessStatus overallFreshness = freshnessService.worstCase(signals.getSourceFreshness().values());

        // Priority 1: hard blocks
        if (signals.isHealthAdvisoryActive() &&
                isFreshOrStale(signals.getSourceFreshness().get(SourceType.HEALTH_ADVISORY))) {
            reasons.add(ReasonCode.HEALTH_ADVISORY_ACTIVE);
            String advisoryDetails = signals.getHealthAdvisoryMessage();
            String summary = (advisoryDetails != null && !advisoryDetails.isBlank())
                    ? advisoryDetails
                    : "Предупреждение по качеству воздуха в районе пляжа (источник: Open-Meteo/CAMS).";
            return buildDecision(signals, Recommendation.DO_NOT_RECOMMEND, Confidence.HIGH,
                    reasons, overallFreshness, missing,
                    summary);
        }

        if (signals.isBeachClosed()) {
            reasons.add(ReasonCode.BEACH_TEMPORARILY_CLOSED);
            return buildDecision(signals, Recommendation.DO_NOT_RECOMMEND, Confidence.HIGH,
                    reasons, overallFreshness, missing,
                    "Пляж временно закрыт.");
        }

        if (signals.getSeaRiskLevel() == SeaRiskLevel.SEVERE &&
                isFreshOrStale(signals.getSourceFreshness().get(SourceType.SEA_FORECAST))) {
            reasons.add(ReasonCode.SEA_RISK_SEVERE);
            return buildDecision(signals, Recommendation.DO_NOT_RECOMMEND, Confidence.HIGH,
                    reasons, overallFreshness, missing,
                    "Купаться не рекомендуется: опасные условия на море.");
        }

        // Priority 3: no fresh data at all (check before priority 2 cautions)
        boolean hasAnyFreshData = signals.getSourceFreshness().values().stream()
                .anyMatch(s -> s == FreshnessStatus.FRESH || s == FreshnessStatus.STALE);

        if (!hasAnyFreshData || overallFreshness == FreshnessStatus.EXPIRED) {
            reasons.add(ReasonCode.NO_FRESH_DATA);
            return buildDecision(signals, Recommendation.UNKNOWN, Confidence.LOW,
                    reasons, overallFreshness, missing,
                    "Актуальных данных недостаточно для уверенной рекомендации.");
        }

        // Priority 2: caution conditions
        boolean cautionflag = false;
        Confidence confidence = Confidence.HIGH;

        if (signals.getSeaRiskLevel() == SeaRiskLevel.HIGH ||
                signals.getSeaRiskLevel() == SeaRiskLevel.MODERATE) {
            reasons.add(ReasonCode.SEA_RISK_HIGH);
            cautionflag = true;
        }

        if (signals.isLifeguardScheduleKnown()
                && !signals.isLifeguardOnDuty()
                && signals.getSeaRiskLevel() != SeaRiskLevel.SEVERE) {
            reasons.add(ReasonCode.LIFEGUARDS_OFF_DUTY);
            cautionflag = true;
        }

        if (signals.getJellyfishSeverity() == JellyfishSeverity.HIGH) {
            reasons.add(ReasonCode.JELLYFISH_REPORTS_HIGH);
            cautionflag = true;
        }

        // Source conflict detection: health advisory present but stale, downgrade confidence
        FreshnessStatus advisoryFreshness = signals.getSourceFreshness().get(SourceType.HEALTH_ADVISORY);
        if (!missing.isEmpty() || advisoryFreshness == FreshnessStatus.STALE) {
            confidence = Confidence.MEDIUM;
        }
        if (missing.size() >= 2) {
            confidence = Confidence.LOW;
        }

        if (overallFreshness == FreshnessStatus.STALE) {
            confidence = confidence == Confidence.HIGH ? Confidence.MEDIUM : confidence;
        }

        if (cautionflag) {
            if (reasons.size() > 3) {
                reasons = reasons.subList(0, 3);
            }
            return buildDecision(signals, Recommendation.CAUTION, confidence,
                    reasons, overallFreshness, missing,
                    buildCautionSummary(reasons));
        }

        // Priority 4: all good
        if (reasons.size() > 3) {
            reasons = reasons.subList(0, 3);
        }
        return buildDecision(signals, Recommendation.CAN_SWIM, confidence,
                reasons, overallFreshness, missing,
                "Море спокойное, купаться можно.");
    }

    private boolean isFreshOrStale(FreshnessStatus status) {
        return status == FreshnessStatus.FRESH || status == FreshnessStatus.STALE;
    }

    private String buildCautionSummary(List<ReasonCode> reasons) {
        if (reasons.contains(ReasonCode.SEA_RISK_HIGH)) {
            return "Купайтесь осторожно: повышенное волнение на море.";
        }
        if (reasons.contains(ReasonCode.JELLYFISH_REPORTS_HIGH)) {
            return "Купайтесь осторожно: зафиксированы медузы.";
        }
        if (reasons.contains(ReasonCode.LIFEGUARDS_OFF_DUTY)) {
            return "Купайтесь осторожно: спасатели не дежурят.";
        }
        return "Купайтесь осторожно.";
    }

    private BeachDecision buildDecision(BeachSignals signals,
                                        Recommendation recommendation,
                                        Confidence confidence,
                                        List<ReasonCode> reasons,
                                        FreshnessStatus freshness,
                                        List<SourceType> missing,
                                        String summary) {
        return BeachDecision.builder()
                .beachSlug(signals.getBeachSlug())
                .beachDisplayName(signals.getBeachDisplayName())
                .city(signals.getCity())
                .recommendation(recommendation)
                .confidence(confidence)
                .reasonCodes(List.copyOf(reasons))
                .humanSummary(summary)
                .lifeguardScheduleKnown(signals.isLifeguardScheduleKnown())
                .lifeguardOnDuty(signals.isLifeguardOnDuty())
                .waveHeightM(signals.getWaveHeightM())
                .airTemperatureC(signals.getAirTemperatureC())
                .relativeHumidityPct(signals.getRelativeHumidityPct())
                .uvIndex(signals.getUvIndex())
                .windSpeedMps(signals.getWindSpeedMps())
                .windDirection(signals.getWindDirection())
                .seaTemperatureC(signals.getSeaTemperatureC())
                .freshnessStatus(freshness)
                .generatedAt(TimeUtil.nowInIsrael())
                .effectiveFrom(signals.getSeaValidFrom())
                .effectiveTo(signals.getSeaValidTo())
                .intervalIsInferred(signals.isSeaIntervalIsInferred())
                .sourceFreshness(Map.copyOf(signals.getSourceFreshness()))
                .missingSourceTypes(List.copyOf(missing))
                .sourceCapturedAt(Map.copyOf(signals.getSourceCapturedAt()))
                .build();
    }
}
