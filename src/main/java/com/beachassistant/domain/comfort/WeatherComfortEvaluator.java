package com.beachassistant.domain.comfort;

import com.beachassistant.domain.model.BeachDecision;
import org.springframework.stereotype.Component;

/**
 * Heuristic weather comfort label from forecast fields on {@link BeachDecision}.
 */
@Component
public class WeatherComfortEvaluator {

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
            return "некомфортно";
        }
        if (score >= 2) {
            return "условно комфортно";
        }
        return "комфортно";
    }
}
