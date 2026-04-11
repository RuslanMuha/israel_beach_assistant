package com.beachassistant.telegram.formatter;

import com.beachassistant.common.enums.ReasonCode;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.TimeUtil;
import com.beachassistant.domain.flag.SwimFlagKnowledge;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachFacilities;
import com.beachassistant.domain.model.BeachProfile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maps {@link BeachDecision} and profile data into a {@link StatusCardModel} (Russian copy, compact).
 */
@Component
public class StatusCardModelMapper {

    private static final int MAX_FACILITY_LABELS = 5;

    public StatusCardModel toModel(BeachDecision decision, BeachProfile profile, boolean hasCamera) {
        String beachName = decision.getBeachDisplayName();
        String city = decision.getCity();
        return new StatusCardModel(
                beachName,
                city,
                overallEmoji(decision.getRecommendation()),
                overallLabel(decision.getRecommendation()),
                shortHumanRecommendation(decision),
                flagColorRu(decision),
                lifeguardStatusRu(decision),
                formatWave(decision),
                formatWater(decision),
                formatAir(decision),
                formatWindCompact(decision),
                formatUvSummary(decision.getUvIndex()),
                formatBeachTypes(profile),
                formatKeyFacilities(profile != null ? profile.facilities() : null),
                formatCameraLine(hasCamera),
                TimeUtil.formatTimeClock(decision.getGeneratedAt()),
                shortSourceSummary(decision)
        );
    }

    private static String overallEmoji(Recommendation r) {
        return switch (r) {
            case CAN_SWIM -> "✅";
            case CAUTION -> "⚠️";
            case DO_NOT_RECOMMEND -> "🚫";
            case UNKNOWN -> "❓";
        };
    }

    private static String overallLabel(Recommendation r) {
        return switch (r) {
            case CAN_SWIM -> "Благоприятно";
            case CAUTION -> "Осторожно";
            case DO_NOT_RECOMMEND -> "Не рекомендуется";
            case UNKNOWN -> "Неизвестно";
        };
    }

    /**
     * Human-readable one-liner; avoids raw enum labels and trims technical wording from upstream summaries.
     */
    String shortHumanRecommendation(BeachDecision d) {
        String raw = d.getHumanSummary();
        if (acceptableHumanSummary(raw)) {
            return trimOneLine(raw);
        }
        return deriveShortRecommendation(d);
    }

    private static boolean acceptableHumanSummary(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (lower.contains("open-meteo") || lower.contains("cams") || lower.contains("inaturalist")) {
            return false;
        }
        if (lower.contains("источник") && (lower.contains("качеств") || lower.contains("воздух"))) {
            return false;
        }
        return true;
    }

    private static String trimOneLine(String raw) {
        String first = raw.split("\\R", 2)[0].trim();
        if (first.length() > 160) {
            return first.substring(0, 157) + "...";
        }
        return first;
    }

    private String deriveShortRecommendation(BeachDecision d) {
        Recommendation rec = d.getRecommendation();
        List<ReasonCode> reasons = d.getReasonCodes();
        if (rec == Recommendation.UNKNOWN) {
            return "Недостаточно данных для уверенной оценки.";
        }
        if (rec == Recommendation.DO_NOT_RECOMMEND) {
            if (reasons.contains(ReasonCode.BEACH_TEMPORARILY_CLOSED)) {
                return "Пляж временно закрыт.";
            }
            if (reasons.contains(ReasonCode.SEA_RISK_SEVERE)) {
                return "Купание сейчас небезопасно.";
            }
            if (reasons.contains(ReasonCode.HEALTH_ADVISORY_ACTIVE)) {
                return "Купание сейчас не рекомендуется.";
            }
            return "Купание сейчас не рекомендуется.";
        }
        if (rec == Recommendation.CAN_SWIM) {
            return "Условия благоприятные для купания.";
        }
        if (reasons.contains(ReasonCode.SEA_RISK_HIGH)) {
            return "Сильное волнение — купайтесь осторожно.";
        }
        if (reasons.contains(ReasonCode.JELLYFISH_REPORTS_HIGH)) {
            return "Сообщается о медузах — будьте осторожны.";
        }
        if (reasons.contains(ReasonCode.LIFEGUARDS_OFF_DUTY)) {
            return "Купание без спасателей не рекомендуется.";
        }
        return "Будьте осторожны при купании.";
    }

    /**
     * Municipal flag colour aligned with {@link SwimFlagKnowledge} naming (no legend).
     */
    String flagColorRu(BeachDecision d) {
        Recommendation rec = d.getRecommendation();
        List<ReasonCode> reasons = d.getReasonCodes();
        if (rec == Recommendation.UNKNOWN) {
            return null;
        }
        if (rec == Recommendation.DO_NOT_RECOMMEND) {
            if (reasons.contains(ReasonCode.BEACH_TEMPORARILY_CLOSED)) {
                return SwimFlagKnowledge.BLACK.colorNameRu();
            }
            return SwimFlagKnowledge.RED.colorNameRu();
        }
        if (rec == Recommendation.CAUTION) {
            return SwimFlagKnowledge.YELLOW.colorNameRu();
        }
        return SwimFlagKnowledge.GREEN.colorNameRu();
    }

    String lifeguardStatusRu(BeachDecision d) {
        if (!d.isLifeguardScheduleKnown()) {
            return "по расписанию города";
        }
        return d.isLifeguardOnDuty() ? "дежурят" : "не дежурят";
    }

    private static String formatWave(BeachDecision d) {
        return formatMetric(d.getWaveHeightM(), " м");
    }

    private static String formatWater(BeachDecision d) {
        return formatMetric(d.getSeaTemperatureC(), "°C");
    }

    private static String formatAir(BeachDecision d) {
        return formatMetric(d.getAirTemperatureC(), "°C");
    }

    private static String formatMetric(Double value, String unit) {
        if (value == null) {
            return null;
        }
        return String.format(Locale.US, "%.1f", value).replace(".0", "") + unit;
    }

    private static String formatWindCompact(BeachDecision d) {
        String speed = formatMetric(d.getWindSpeedMps(), " м/с");
        if (speed == null) {
            return null;
        }
        String dir = d.getWindDirection();
        if (dir == null || dir.isBlank()) {
            return speed;
        }
        String abbrev = windAbbrevRu(dir.trim());
        return speed + ", " + abbrev;
    }

    /**
     * Short meteorological bearing (e.g. NW → СЗ).
     */
    static String windAbbrevRu(String compass8) {
        String c = compass8.toUpperCase(Locale.ROOT);
        return switch (c) {
            case "N" -> "С";
            case "NNE", "NE", "ENE" -> "СВ";
            case "E" -> "В";
            case "ESE", "SE", "SSE" -> "ЮВ";
            case "S" -> "Ю";
            case "SSW", "SW", "WSW" -> "ЮЗ";
            case "W" -> "З";
            case "WNW", "NW", "NNW" -> "СЗ";
            default -> c;
        };
    }

    private static String formatUvSummary(Double uv) {
        if (uv == null) {
            return null;
        }
        if (uv < 3) {
            return "низкий";
        }
        if (uv < 6) {
            return "умеренный";
        }
        if (uv < 8) {
            return "высокий";
        }
        if (uv < 11) {
            return "очень высокий";
        }
        return "экстремальный";
    }

    private static String formatBeachTypes(BeachProfile profile) {
        if (profile == null || profile.categories().isEmpty()) {
            return null;
        }
        return profile.categories().stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.joining(", "));
    }

    String formatKeyFacilities(BeachFacilities f) {
        if (f == null || f.equals(BeachFacilities.empty())) {
            return null;
        }
        Map<String, Boolean> m = new LinkedHashMap<>();
        m.put("душ", f.showers());
        m.put("туалет", f.toilets());
        m.put("спорт", f.sportsFacilities());
        m.put("парковка", f.parking());
        m.put("площадка", f.playground());
        m.put("доступность", f.accessible());
        List<String> labels = m.entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .limit(MAX_FACILITY_LABELS)
                .toList();
        if (labels.isEmpty()) {
            return null;
        }
        return String.join(", ", labels);
    }

    private static String formatCameraLine(boolean hasCamera) {
        if (!hasCamera) {
            return null;
        }
        return "доступна";
    }

    Optional<String> shortSourceSummary(BeachDecision d) {
        List<String> parts = new ArrayList<>();
        if (d.isLifeguardScheduleKnown()) {
            parts.add("муниципалитет");
        }
        var cap = d.getSourceCapturedAt();
        if (cap != null && (cap.containsKey(SourceType.SEA_FORECAST) || cap.containsKey(SourceType.HEALTH_ADVISORY))) {
            parts.add("meteo");
        }
        if (parts.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of("Данные: " + String.join(" + ", parts));
    }
}
