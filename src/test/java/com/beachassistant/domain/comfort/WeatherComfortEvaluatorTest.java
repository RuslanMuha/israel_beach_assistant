package com.beachassistant.domain.comfort;

import com.beachassistant.common.enums.Confidence;
import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.i18n.I18n;
import com.beachassistant.i18n.I18nTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WeatherComfortEvaluatorTest {

    private WeatherComfortEvaluator evaluator;

    @BeforeEach
    void setUp() {
        I18n i18n = I18nTestConfig.ru();
        evaluator = new WeatherComfortEvaluator(i18n);
        LocaleContextHolder.setLocale(Locale.forLanguageTag("ru"));
    }

    @AfterEach
    void tearDown() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void mildConditions_comfortable() {
        BeachDecision d = baseDecision()
                .airTemperatureC(24.0)
                .relativeHumidityPct(50.0)
                .windSpeedMps(4.0)
                .uvIndex(2.0)
                .build();
        assertThat(evaluator.comfortLabel(d)).isEqualTo("комфортно");
    }

    @Test
    void extremeHeat_uncomfortable() {
        BeachDecision d = baseDecision()
                .airTemperatureC(35.0)
                .relativeHumidityPct(50.0)
                .windSpeedMps(12.0)
                .uvIndex(2.0)
                .build();
        assertThat(evaluator.comfortLabel(d)).isEqualTo("некомфортно");
    }

    private BeachDecision.BeachDecisionBuilder baseDecision() {
        return BeachDecision.builder()
                .beachSlug("x")
                .beachDisplayName("X")
                .city("Ashdod")
                .recommendation(Recommendation.CAN_SWIM)
                .confidence(Confidence.HIGH)
                .reasonCodes(List.of())
                .humanSummary(null)
                .freshnessStatus(FreshnessStatus.FRESH)
                .generatedAt(ZonedDateTime.now())
                .effectiveFrom(null)
                .effectiveTo(null)
                .intervalIsInferred(false)
                .sourceFreshness(Map.of())
                .missingSourceTypes(List.of());
    }
}
