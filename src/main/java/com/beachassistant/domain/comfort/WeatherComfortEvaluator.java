package com.beachassistant.domain.comfort;

import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.i18n.I18n;
import org.springframework.stereotype.Component;

/**
 * Heuristic weather comfort label from forecast fields on {@link BeachDecision}.
 */
@Component
public class WeatherComfortEvaluator {

    private final I18n i18n;

    public WeatherComfortEvaluator(I18n i18n) {
        this.i18n = i18n;
    }

    public String comfortLabel(BeachDecision d) {
        int score = 0;
        Double temp = d.getAirTemperatureC();
        Double hum = d.getRelativeHumidityPct();
        Double wind = d.getWindSpeedMps();
        Double uv = d.getUvIndex();
        if (temp != null && (temp >= 33 || temp <= 12)) {
            score += 2;
        } else if (temp != null && (temp >= 30 || temp <= 15)) {
            score += 1;
        }
        if (hum != null && hum >= 80) {
            score += 1;
        }
        if (wind != null && wind >= 12) {
            score += 2;
        } else if (wind != null && wind >= 8) {
            score += 1;
        }
        if (uv != null && uv >= 11) {
            score += 2;
        } else if (uv != null && uv >= 8) {
            score += 1;
        }
        if (score >= 4) {
            return i18n.t("comfort.uncomfortable");
        }
        if (score >= 2) {
            return i18n.t("comfort.marginal");
        }
        return i18n.t("comfort.comfortable");
    }
}
