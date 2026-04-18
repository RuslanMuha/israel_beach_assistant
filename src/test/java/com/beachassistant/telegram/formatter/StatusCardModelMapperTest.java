package com.beachassistant.telegram.formatter;

import com.beachassistant.common.enums.Confidence;
import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.ReasonCode;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachFacilities;
import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.i18n.I18n;
import com.beachassistant.i18n.I18nTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCardModelMapperTest {

    private StatusCardModelMapper mapper;
    private I18n i18n;

    @BeforeEach
    void setUp() {
        i18n = I18nTestConfig.ru();
        mapper = new StatusCardModelMapper(i18n);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void windAbbrevRu_mapsEightPointCompass() {
        assertThat(StatusCardModelMapper.windAbbrevRu("NW")).isEqualTo("СЗ");
        assertThat(StatusCardModelMapper.windAbbrevRu("N")).isEqualTo("С");
    }

    @Test
    void lifeguard_unknownSchedule_usesCitySchedulePhrase() {
        BeachDecision d = decisionBase()
                .recommendation(Recommendation.CAN_SWIM)
                .reasonCodes(List.of())
                .humanSummary("Условия благоприятные для купания.")
                .lifeguardScheduleKnown(false)
                .lifeguardOnDuty(false)
                .build();
        assertThat(mapper.toModel(d, BeachProfile.empty(), false).lifeguardLine()).isEqualTo("по расписанию города");
    }

    @Test
    void shortHumanRecommendation_prefersCleanSummaryOverTechnical() {
        BeachDecision d = decisionBase()
                .recommendation(Recommendation.DO_NOT_RECOMMEND)
                .reasonCodes(List.of(ReasonCode.HEALTH_ADVISORY_ACTIVE))
                .humanSummary("Предупреждение по качеству воздуха в районе пляжа (источник: Open-Meteo/CAMS).")
                .build();
        assertThat(mapper.shortHumanRecommendation(d)).isEqualTo("Купание сейчас не рекомендуется.");
    }

    private BeachDecision.BeachDecisionBuilder decisionBase() {
        ZonedDateTime now = ZonedDateTime.parse("2026-04-10T20:31:00+03:00[Asia/Jerusalem]");
        Map<SourceType, ZonedDateTime> cap = new EnumMap<>(SourceType.class);
        cap.put(SourceType.SEA_FORECAST, now);
        cap.put(SourceType.LIFEGUARD_SCHEDULE, now);
        return BeachDecision.builder()
                .beachSlug("lido")
                .beachDisplayName("Lido")
                .city("Ashdod")
                .recommendation(Recommendation.CAUTION)
                .confidence(Confidence.HIGH)
                .reasonCodes(List.of(ReasonCode.LIFEGUARDS_OFF_DUTY))
                .humanSummary("Купайтесь осторожно: спасатели не дежурят.")
                .lifeguardScheduleKnown(true)
                .lifeguardOnDuty(false)
                .waveHeightM(0.9)
                .airTemperatureC(16.8)
                .relativeHumidityPct(55.0)
                .uvIndex(2.0)
                .windSpeedMps(0.9)
                .windDirection("NW")
                .seaTemperatureC(18.5)
                .freshnessStatus(FreshnessStatus.FRESH)
                .generatedAt(now)
                .effectiveFrom(null)
                .effectiveTo(null)
                .intervalIsInferred(false)
                .sourceFreshness(Map.of())
                .missingSourceTypes(List.of())
                .sourceCapturedAt(cap);
    }

    @Test
    void freshnessBadge_returnsWorstCaseDotAcrossPrimarySources() {
        Map<SourceType, FreshnessStatus> f = new EnumMap<>(SourceType.class);
        f.put(SourceType.SEA_FORECAST, FreshnessStatus.FRESH);
        f.put(SourceType.HEALTH_ADVISORY, FreshnessStatus.STALE);
        BeachDecision d = decisionBase().sourceFreshness(f).build();
        assertThat(mapper.freshnessBadge(d)).contains("🟡").contains("частично");
    }

    @Test
    void freshnessBadge_nullWhenNoFreshnessData() {
        BeachDecision d = decisionBase().sourceFreshness(Map.of()).build();
        assertThat(mapper.freshnessBadge(d)).isNull();
    }

    @Test
    void facilities_cappedAndShortLabels() {
        BeachFacilities f = new BeachFacilities(true, true, true, true, true, true);
        BeachProfile p = new BeachProfile(null, List.of("family"), f, null, null, null, null, null, null);
        BeachDecision d = decisionBase().build();
        assertThat(mapper.formatKeyFacilities(p.facilities())).contains("душ");
        assertThat(mapper.formatKeyFacilities(p.facilities()).split(", ")).hasSizeLessThanOrEqualTo(5);
    }
}
