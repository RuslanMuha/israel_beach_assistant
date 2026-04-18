package com.beachassistant.telegram.formatter;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.TimeUtil;
import com.beachassistant.domain.comfort.WeatherComfortEvaluator;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.i18n.I18n;
import com.beachassistant.web.dto.JellyfishDto;
import com.beachassistant.web.dto.LifeguardHoursDto;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Telegram messages for secondary views (details, hours, jellyfish, camera). Main beach status uses
 * {@link StatusCardTemplate}; locale comes from {@link org.springframework.context.i18n.LocaleContextHolder}.
 */
@Component
public class ResponseFormatter {

    private final WeatherComfortEvaluator weatherComfortEvaluator;
    private final StatusCardModelMapper statusCardModelMapper;
    private final I18n i18n;

    public ResponseFormatter(WeatherComfortEvaluator weatherComfortEvaluator,
                             StatusCardModelMapper statusCardModelMapper,
                             I18n i18n) {
        this.weatherComfortEvaluator = weatherComfortEvaluator;
        this.statusCardModelMapper = statusCardModelMapper;
        this.i18n = i18n;
    }

    public String formatStatus(BeachDecision decision, BeachProfile profile, boolean hasCamera) {
        StatusCardModel model = statusCardModelMapper.toModel(decision, profile, hasCamera);
        return StatusCardTemplate.format(model, i18n);
    }

    /**
     * Secondary view: comfort, forecast window, source transparency, flag legend, profile notes — not the main card.
     */
    public String formatStatusDetails(BeachDecision decision, BeachProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(LegendSection.DETAILS_EMOJI).append(" ").append(i18n.t("details.header", decision.getBeachDisplayName(), decision.getCity()))
                .append("\n\n");

        sb.append(LegendSection.WEATHER_EMOJI).append(" ").append(i18n.t("details.more")).append("\n");
        if (decision.getRelativeHumidityPct() != null) {
            sb.append(LegendSection.HUMIDITY_EMOJI).append(" ").append(i18n.t("details.humidity", formatNumber(decision.getRelativeHumidityPct(), "%"))).append("\n");
        }
        sb.append(i18n.t("details.comfort")).append(" ").append(weatherComfortEvaluator.comfortLabel(decision)).append("\n");

        if (!decision.getReasonCodes().isEmpty()) {
            sb.append(i18n.t("details.factors")).append(" ")
                    .append(decision.getReasonCodes().stream()
                            .map(r -> i18n.t("reason." + r.name()))
                            .collect(Collectors.joining(", ")))
                    .append("\n");
        }

        if (decision.getEffectiveFrom() != null && decision.getEffectiveTo() != null) {
            sb.append("\n").append(LegendSection.CLOCK_EMOJI).append(" ").append(i18n.t("details.forecast_window")).append("\n");
            sb.append(i18n.t("details.interval_from", TimeUtil.formatForDisplay(decision.getEffectiveFrom()))).append("\n");
            sb.append(i18n.t("details.interval_to", TimeUtil.formatForDisplay(decision.getEffectiveTo()))).append("\n");
            if (decision.isIntervalIsInferred()) {
                sb.append(i18n.t("details.interval_inferred"));
            }
        }

        sb.append("\n").append(LegendSection.SOURCE_EMOJI).append(" ").append(i18n.t("details.sources")).append("\n");
        for (String sourceLine : sourceLines(decision)) {
            sb.append("• ").append(sourceLine).append("\n");
        }

        appendBeachProfileDetails(sb, profile);

        sb.append("\n").append(LegendSection.DETAILS_EMOJI).append(" ").append(i18n.t("details.flag_legend")).append("\n");
        sb.append(i18n.t("flags.legend.compact")).append("\n");

        sb.append("\n").append(i18n.t("details.confidence")).append(" ").append(confidenceLabel(decision)).append("\n");
        sb.append(i18n.t("details.generated")).append(" ").append(TimeUtil.formatForDisplay(decision.getGeneratedAt()))
                .append("\n");

        return sb.toString();
    }

    private void appendBeachProfileDetails(StringBuilder sb, BeachProfile profile) {
        if (profile == null || profile.isEmpty()) {
            return;
        }
        sb.append("\n").append(LegendSection.INFO_EMOJI).append(" ").append(i18n.t("details.about")).append("\n");
        if (profile.description() != null && !profile.description().isBlank()) {
            sb.append(profile.description()).append("\n");
        }
        if (profile.accessibilityNotes() != null && !profile.accessibilityNotes().isBlank()) {
            sb.append(i18n.t("details.accessibility")).append(" ").append(profile.accessibilityNotes()).append("\n");
        }
        if (profile.parkingNotes() != null && !profile.parkingNotes().isBlank()) {
            sb.append(i18n.t("details.parking")).append(" ").append(profile.parkingNotes()).append("\n");
        }
        if (profile.notes() != null && !profile.notes().isBlank()) {
            sb.append(i18n.t("details.notes")).append(" ").append(profile.notes()).append("\n");
        }
        if (profile.lifeguardNotes() != null && !profile.lifeguardNotes().isBlank()) {
            sb.append(i18n.t("details.lifeguards_section")).append(" ").append(profile.lifeguardNotes()).append("\n");
        }
        if (profile.waterQualityPlaceholder() != null && !profile.waterQualityPlaceholder().isBlank()) {
            sb.append(i18n.t("details.water_quality")).append(" ").append(profile.waterQualityPlaceholder()).append("\n");
        }
        if (profile.jellyfishPlaceholder() != null && !profile.jellyfishPlaceholder().isBlank()) {
            sb.append(i18n.t("details.jellyfish_section")).append(" ").append(profile.jellyfishPlaceholder()).append("\n");
        }
    }

    public String formatHours(LifeguardHoursDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(LegendSection.LIFEGUARD_EMOJI).append(" ").append(i18n.t("hours.title", dto.getBeach())).append("\n\n");

        if (dto.isOnDuty()) {
            sb.append(i18n.t("hours.on_duty", dto.getCloseTime())).append("\n");
        } else {
            sb.append(i18n.t("hours.off_duty")).append("\n");
            if (dto.getOpenTime() != null) {
                sb.append(i18n.t("hours.schedule", dto.getOpenTime(), dto.getCloseTime())).append("\n");
            }
        }

        appendFreshnessNote(sb, dto.getFreshnessStatus(), dto.getCapturedAt());
        return sb.toString();
    }

    public String formatJellyfish(JellyfishDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(LegendSection.JELLYFISH_EMOJI).append(" ").append(i18n.t("jellyfish.title", dto.getBeach())).append("\n\n");

        sb.append(i18n.t("jellyfish.level")).append(" ").append(jellyfishSeverityLabel(dto)).append("\n");

        if (dto.getReportCount() != null && dto.getReportCount() > 0) {
            sb.append(i18n.t("jellyfish.reports")).append(" ").append(dto.getReportCount()).append("\n");
        }

        if (dto.getWindowStart() != null && dto.getWindowEnd() != null) {
            sb.append(i18n.t("jellyfish.period")).append(" ")
                    .append(TimeUtil.formatForDisplay(dto.getWindowStart()))
                    .append(" — ")
                    .append(TimeUtil.formatForDisplay(dto.getWindowEnd()))
                    .append("\n");
        }

        appendFreshnessNote(sb, dto.getFreshnessStatus(), dto.getCapturedAt());
        return sb.toString();
    }

    public String formatCameraLive(String beachName, String liveUrl, String healthStatus) {
        if ("UNREACHABLE".equalsIgnoreCase(healthStatus)) {
            return LegendSection.CAMERA_EMOJI + " " + i18n.t("camera.live_title", beachName) + "\n\n⚠ " + i18n.t("camera.unreachable_upstream");
        }
        return LegendSection.CAMERA_EMOJI + " " + i18n.t("camera.live_title", beachName) + "\n\n🔴 " + i18n.t("camera.live_stream") + "\n" + liveUrl;
    }

    public String formatCameraUnavailable(String beachName) {
        return LegendSection.CAMERA_EMOJI + " " + beachName + "\n\n" + i18n.t("camera.not_configured");
    }

    public String formatCameraTemporarilyUnavailable(String beachName) {
        return LegendSection.CAMERA_EMOJI + " " + i18n.t("camera.live_title", beachName)
                + "\n\n⚠ " + i18n.t("camera.provider_down");
    }

    public String formatBeachList(java.util.List<com.beachassistant.persistence.entity.BeachEntity> beaches) {
        String city = beaches.isEmpty() ? "" : beaches.get(0).getCity().getName();
        StringBuilder sb = new StringBuilder(LegendSection.BEACH_EMOJI).append(" ").append(i18n.t("beaches.list.title", city)).append("\n\n");
        for (var beach : beaches) {
            sb.append("• ").append(beach.getDisplayName());
            if (beach.isHasLifeguards()) {
                sb.append(" ").append(LegendSection.LIFEGUARD_EMOJI);
            }
            if (beach.isHasCamera()) {
                sb.append(" ").append(LegendSection.CAMERA_EMOJI);
            }
            sb.append("\n");
        }
        sb.append(i18n.t("beaches.list.commands_footer"));
        return sb.toString();
    }

    public String formatWelcome(java.util.List<com.beachassistant.persistence.entity.BeachEntity> beaches) {
        String names = beaches.stream()
                .map(com.beachassistant.persistence.entity.BeachEntity::getDisplayName)
                .collect(java.util.stream.Collectors.joining(", "));
        return i18n.t("welcome.greeting") + "\n\n"
                + i18n.t("welcome.supported_beaches", names) + "\n\n"
                + i18n.t("welcome.commands_header") + "\n"
                + i18n.t("welcome.commands_lines");
    }

    private String confidenceLabel(BeachDecision d) {
        return i18n.t("confidence." + d.getConfidence().name());
    }

    private String jellyfishSeverityLabel(JellyfishDto dto) {
        boolean noFreshData = dto.getFreshnessStatus() == FreshnessStatus.EXPIRED
                || (dto.getFreshnessStatus() == FreshnessStatus.STALE
                && (dto.getCapturedAt() == null
                || dto.getReportCount() == null
                || dto.getReportCount() == 0));
        if (dto.getSeverityLevel() == null || noFreshData) {
            return i18n.t("jellyfish.unknown_no_obs");
        }
        return i18n.t("jellyfish.level." + dto.getSeverityLevel().name());
    }

    private void appendFreshnessNote(StringBuilder sb, FreshnessStatus freshness,
                                     java.time.ZonedDateTime capturedAt) {
        if (freshness == FreshnessStatus.STALE) {
            sb.append("\n").append(i18n.t("freshness.STALE.note", TimeUtil.formatForDisplay(capturedAt)));
        } else if (freshness == FreshnessStatus.EXPIRED) {
            if (capturedAt == null) {
                sb.append("\n").append(i18n.t("freshness.EXPIRED.no_data"));
            } else {
                sb.append("\n").append(i18n.t("freshness.EXPIRED.note", TimeUtil.formatForDisplay(capturedAt)));
            }
        }
    }

    private List<String> sourceLines(BeachDecision decision) {
        List<String> lines = new ArrayList<>();
        lines.add(sourceLine(decision, SourceType.SEA_FORECAST, LegendSection.SEA_EMOJI + " " + i18n.t("source.sea_caption")));
        lines.add(sourceLine(decision, SourceType.HEALTH_ADVISORY, LegendSection.AIR_EMOJI + " " + i18n.t("source.air_caption")));
        lines.add(sourceLine(decision, SourceType.LIFEGUARD_SCHEDULE, LegendSection.LIFEGUARD_EMOJI + " " + i18n.t("source.lifeguard_caption")));
        lines.add(sourceLine(decision, SourceType.JELLYFISH, LegendSection.JELLYFISH_EMOJI + " " + i18n.t("source.jellyfish_caption")));
        return lines;
    }

    private String sourceLine(BeachDecision decision, SourceType type, String label) {
        if (decision.getMissingSourceTypes().contains(type)) {
            return label + i18n.t("source.line.missing_suffix");
        }
        FreshnessStatus f = decision.getSourceFreshness() != null ? decision.getSourceFreshness().get(type) : null;
        String freshnessWord = i18n.t("freshness.source." + (f != null ? f : FreshnessStatus.EXPIRED).name());
        ZonedDateTime cap = decision.getSourceCapturedAt() != null
                ? decision.getSourceCapturedAt().get(type)
                : null;
        String timePart = cap != null
                ? i18n.t("source.line.updated", formatAge(cap), TimeUtil.formatForDisplay(cap))
                : i18n.t("source.line.time_unknown");
        return label + " — " + freshnessWord + ", " + timePart;
    }

    private String formatAge(ZonedDateTime capturedAt) {
        Duration age = Duration.between(capturedAt, TimeUtil.nowInIsrael());
        long minutes = Math.max(0, age.toMinutes());
        if (minutes < 60) {
            return i18n.t("age.minutes", minutes);
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (remMinutes == 0) {
            return i18n.t("age.hours", hours);
        }
        return i18n.t("age.hours_minutes", hours, remMinutes);
    }

    private String formatNumber(Double value, String unit) {
        if (value == null) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%.1f", value).replace(".0", "") + unit;
    }
}
