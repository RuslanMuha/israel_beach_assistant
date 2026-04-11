package com.beachassistant.decision;

import com.beachassistant.common.enums.*;
import com.beachassistant.decision.engine.DecisionEngine;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachSignals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionEngineTest {

    private DecisionEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DecisionEngine();
    }

    @Test
    void activeHealthAdvisory_shouldReturnDoNotRecommend() {
        BeachSignals signals = signalsBuilder()
                .healthAdvisoryActive(true)
                .advisoryCapturedAt(ZonedDateTime.now())
                .sourceFreshness(freshMap(SourceType.HEALTH_ADVISORY, FreshnessStatus.FRESH))
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.DO_NOT_RECOMMEND);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.HEALTH_ADVISORY_ACTIVE);
    }

    @Test
    void seaRiskSevere_shouldReturnDoNotRecommend() {
        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.SEVERE)
                .seaCapturedAt(ZonedDateTime.now())
                .sourceFreshness(freshMap(SourceType.SEA_FORECAST, FreshnessStatus.FRESH))
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.DO_NOT_RECOMMEND);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.SEA_RISK_SEVERE);
    }

    @Test
    void seaRiskHigh_shouldReturnCaution() {
        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.HIGH)
                .seaCapturedAt(ZonedDateTime.now())
                .lifeguardOnDuty(true)
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.CAUTION);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.SEA_RISK_HIGH);
    }

    @Test
    void lifeguardsOffDuty_seaCalm_shouldReturnCaution() {
        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.CALM)
                .lifeguardOnDuty(false)
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.CAUTION);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.LIFEGUARDS_OFF_DUTY);
    }

    @Test
    void noLifeguardSchedule_seaCalm_shouldNotAddLifeguardReason() {
        BeachSignals signals = signalsBuilder()
                .lifeguardScheduleKnown(false)
                .lifeguardOnDuty(false)
                .seaRiskLevel(SeaRiskLevel.CALM)
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.CAN_SWIM);
        assertThat(decision.getReasonCodes()).doesNotContain(ReasonCode.LIFEGUARDS_OFF_DUTY);
    }

    @Test
    void jellyfishHigh_shouldReturnCaution() {
        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.CALM)
                .lifeguardOnDuty(true)
                .jellyfishSeverity(JellyfishSeverity.HIGH)
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.CAUTION);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.JELLYFISH_REPORTS_HIGH);
    }

    @Test
    void allFreshNoRisks_shouldReturnCanSwim() {
        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.CALM)
                .lifeguardOnDuty(true)
                .jellyfishSeverity(JellyfishSeverity.NONE)
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.CAN_SWIM);
    }

    @Test
    void allExpiredData_shouldReturnUnknown() {
        BeachSignals signals = signalsBuilder()
                .sourceFreshness(allExpiredMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.UNKNOWN);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.NO_FRESH_DATA);
    }

    @Test
    void seaFreshButAdvisoryExpired_shouldStillReturnUsableRecommendation() {
        Map<SourceType, FreshnessStatus> freshness = allFreshMap();
        freshness.put(SourceType.HEALTH_ADVISORY, FreshnessStatus.EXPIRED);

        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.CALM)
                .lifeguardOnDuty(true)
                .sourceFreshness(freshness)
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.CAN_SWIM);
        assertThat(decision.getFreshnessStatus()).isEqualTo(FreshnessStatus.STALE);
        assertThat(decision.getConfidence()).isEqualTo(Confidence.LOW);
    }

    @Test
    void advisoryOverridesEverything() {
        BeachSignals signals = signalsBuilder()
                .seaRiskLevel(SeaRiskLevel.CALM)
                .lifeguardOnDuty(true)
                .healthAdvisoryActive(true)
                .advisoryCapturedAt(ZonedDateTime.now())
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.DO_NOT_RECOMMEND);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.HEALTH_ADVISORY_ACTIVE);
    }

    @Test
    void beachClosed_shouldReturnDoNotRecommend() {
        BeachSignals signals = signalsBuilder()
                .beachClosed(true)
                .sourceFreshness(allFreshMap())
                .build();

        BeachDecision decision = engine.evaluate(signals);

        assertThat(decision.getRecommendation()).isEqualTo(Recommendation.DO_NOT_RECOMMEND);
        assertThat(decision.getReasonCodes()).contains(ReasonCode.BEACH_TEMPORARILY_CLOSED);
    }

    // Helpers

    private BeachSignals.BeachSignalsBuilder signalsBuilder() {
        return BeachSignals.builder()
                .beachSlug("yud-alef")
                .beachDisplayName("Yud Alef")
                .city("Ashdod")
                .healthAdvisoryActive(false)
                .beachClosed(false)
                .lifeguardScheduleKnown(true)
                .lifeguardOnDuty(false)
                .jellyfishSeverity(JellyfishSeverity.NONE)
                .seaRiskLevel(SeaRiskLevel.CALM);
    }

    private Map<SourceType, FreshnessStatus> freshMap(SourceType type, FreshnessStatus status) {
        Map<SourceType, FreshnessStatus> map = new EnumMap<>(SourceType.class);
        map.put(type, status);
        return map;
    }

    private Map<SourceType, FreshnessStatus> allFreshMap() {
        Map<SourceType, FreshnessStatus> map = new EnumMap<>(SourceType.class);
        for (SourceType t : SourceType.values()) {
            map.put(t, FreshnessStatus.FRESH);
        }
        return map;
    }

    private Map<SourceType, FreshnessStatus> allExpiredMap() {
        Map<SourceType, FreshnessStatus> map = new EnumMap<>(SourceType.class);
        for (SourceType t : SourceType.values()) {
            map.put(t, FreshnessStatus.EXPIRED);
        }
        return map;
    }
}
