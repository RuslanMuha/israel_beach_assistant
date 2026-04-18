package com.beachassistant.telegram.formatter;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.ReasonCode;
import com.beachassistant.common.enums.Recommendation;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.TimeUtil;
import com.beachassistant.domain.flag.SwimFlagKnowledge;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachFacilities;
import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.i18n.I18n;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Maps {@link BeachDecision} and profile data into a {@link StatusCardModel} (compact card copy).
 */
@Component
public class StatusCardModelMapper {

    private static final int MAX_FACILITY_LABELS = 5;

    private final I18n i18n;

    public StatusCardModelMapper(I18n i18n) {
        this.i18n = i18n;
    }

    public StatusCardModel toModel(BeachDecision decision, BeachProfile profile, boolean hasCamera) {
        String beachName = decision.getBeachDisplayName();
        String city = decision.getCity();
        return new StatusCardModel(
                beachName,
                city,
                LegendSection.recommendation(decision.getRecommendation()),
                overallLabel(decision.getRecommendation()),
                freshnessBadge(decision),
                shortHumanRecommendation(decision),
                flagColorLabel(decision),
                lifeguardStatusLabel(decision),
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

    /**
     * Worst-case freshness badge across the primary signal sources (sea + air).
     * Returns {@code null} when all relevant freshness data is missing (card header stays clean).
     */
    String freshnessBadge(BeachDecision d) {
        Map<SourceType, FreshnessStatus> freshness = d.getSourceFreshness();
        if (freshness == null || freshness.isEmpty()) {
            return null;
        }
        FreshnessStatus worst = null;
        for (SourceType type : List.of(SourceType.SEA_FORECAST, SourceType.HEALTH_ADVISORY)) {
            if (d.getMissingSourceTypes() != null && d.getMissingSourceTypes().contains(type)) {
                continue;
            }
            FreshnessStatus f = freshness.get(type);
            if (f == null) {
                continue;
            }
            if (worst == null || severity(f) > severity(worst)) {
                worst = f;
            }
        }
        if (worst == null) {
            return null;
        }
        return LegendSection.freshnessDot(worst) + " " + i18n.t("freshness.badge." + worst.name());
    }

    private static int severity(FreshnessStatus f) {
        return switch (f) {
            case FRESH -> 0;
            case STALE -> 1;
            case EXPIRED -> 2;
        };
    }

    private String overallLabel(Recommendation r) {
        return i18n.t("rec." + r.name());
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
        if (lower.contains("air quality") || lower.contains("eaqi") || lower.contains("sensitive groups")) {
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
            return i18n.t("summary.insufficient_data");
        }
        if (rec == Recommendation.DO_NOT_RECOMMEND) {
            if (reasons.contains(ReasonCode.BEACH_TEMPORARILY_CLOSED)) {
                return i18n.t("summary.beach_closed");
            }
            if (reasons.contains(ReasonCode.SEA_RISK_SEVERE)) {
                return i18n.t("summary.unsafe_sea");
            }
            if (reasons.contains(ReasonCode.HEALTH_ADVISORY_ACTIVE)) {
                return i18n.t("summary.health_advisory");
            }
            return i18n.t("summary.do_not_recommend");
        }
        if (rec == Recommendation.CAN_SWIM) {
            return i18n.t("summary.favorable");
        }
        if (reasons.contains(ReasonCode.SEA_RISK_HIGH)) {
            return i18n.t("summary.rough_sea");
        }
        if (reasons.contains(ReasonCode.JELLYFISH_REPORTS_HIGH)) {
            return i18n.t("summary.jellyfish");
        }
        if (reasons.contains(ReasonCode.LIFEGUARDS_OFF_DUTY)) {
            return i18n.t("summary.no_lifeguards");
        }
        return i18n.t("summary.default_caution");
    }

    /**
     * Municipal flag colour aligned with {@link SwimFlagKnowledge} naming (no legend).
     */
    String flagColorLabel(BeachDecision d) {
        Recommendation rec = d.getRecommendation();
        List<ReasonCode> reasons = d.getReasonCodes();
        if (rec == Recommendation.UNKNOWN) {
            return null;
        }
        if (rec == Recommendation.DO_NOT_RECOMMEND) {
            if (reasons.contains(ReasonCode.BEACH_TEMPORARILY_CLOSED)) {
                return i18n.t("flag.BLACK.name");
            }
            return i18n.t("flag.RED.name");
        }
        if (rec == Recommendation.CAUTION) {
            return i18n.t("flag.YELLOW.name");
        }
        return i18n.t("flag.GREEN.name");
    }

    String lifeguardStatusLabel(BeachDecision d) {
        if (!d.isLifeguardScheduleKnown()) {
            return i18n.t("lifeguard.by_city_schedule");
        }
        return d.isLifeguardOnDuty() ? i18n.t("lifeguard.on_duty") : i18n.t("lifeguard.off_duty");
    }

    private String formatWave(BeachDecision d) {
        return formatMetric(d.getWaveHeightM(), i18n.t("unit.m"));
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

    private String formatWindCompact(BeachDecision d) {
        String speed = formatMetric(d.getWindSpeedMps(), i18n.t("unit.mps"));
        if (speed == null) {
            return null;
        }
        String dir = d.getWindDirection();
        if (dir == null || dir.isBlank()) {
            return speed;
        }
        String abbrev = windDirectionAbbrev(dir.trim());
        return speed + ", " + abbrev;
    }

    /**
     * Short meteorological bearing (e.g. NW → СЗ in Russian, NW in English).
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

    private String windDirectionAbbrev(String dir) {
        Locale loc = LocaleContextHolder.getLocale();
        if (loc != null && "ru".equalsIgnoreCase(loc.getLanguage())) {
            return windAbbrevRu(dir);
        }
        return dir.trim().toUpperCase(Locale.ROOT);
    }

    private String formatUvSummary(Double uv) {
        if (uv == null) {
            return null;
        }
        if (uv < 3) {
            return i18n.t("uv.low");
        }
        if (uv < 6) {
            return i18n.t("uv.moderate");
        }
        if (uv < 8) {
            return i18n.t("uv.high");
        }
        if (uv < 11) {
            return i18n.t("uv.very_high");
        }
        return i18n.t("uv.extreme");
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
        m.put(i18n.t("facilities.shower"), f.showers());
        m.put(i18n.t("facilities.toilet"), f.toilets());
        m.put(i18n.t("facilities.sport"), f.sportsFacilities());
        m.put(i18n.t("facilities.parking"), f.parking());
        m.put(i18n.t("facilities.playground"), f.playground());
        m.put(i18n.t("facilities.accessibility"), f.accessible());
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

    private String formatCameraLine(boolean hasCamera) {
        if (!hasCamera) {
            return null;
        }
        return i18n.t("camera.available");
    }

    Optional<String> shortSourceSummary(BeachDecision d) {
        List<String> parts = new ArrayList<>();
        if (d.isLifeguardScheduleKnown()) {
            parts.add(i18n.t("source.summary.municipality"));
        }
        var cap = d.getSourceCapturedAt();
        if (cap != null && (cap.containsKey(SourceType.SEA_FORECAST) || cap.containsKey(SourceType.HEALTH_ADVISORY))) {
            parts.add(i18n.t("source.summary.meteo"));
        }
        if (parts.isEmpty()) {
            FreshnessStatus advisoryFreshness = d.getSourceFreshness() != null
                    ? d.getSourceFreshness().get(SourceType.HEALTH_ADVISORY)
                    : null;
            if (advisoryFreshness == FreshnessStatus.EXPIRED) {
                return Optional.of(i18n.t("source.air_stale_note"));
            }
            return Optional.empty();
        }
        StringBuilder line = new StringBuilder(i18n.t("source.summary.prefix") + " " + String.join(" + ", parts));
        FreshnessStatus advisoryFreshness = d.getSourceFreshness() != null
                ? d.getSourceFreshness().get(SourceType.HEALTH_ADVISORY)
                : null;
        if (advisoryFreshness == FreshnessStatus.EXPIRED) {
            line.append(". ").append(i18n.t("source.air_stale_note"));
        }
        return Optional.of(line.toString());
    }
}
