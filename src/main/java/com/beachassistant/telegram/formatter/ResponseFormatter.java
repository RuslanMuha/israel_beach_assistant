package com.beachassistant.telegram.formatter;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.ReasonCode;
import com.beachassistant.common.enums.SourceType;
import com.beachassistant.common.util.TimeUtil;
import com.beachassistant.domain.comfort.WeatherComfortEvaluator;
import com.beachassistant.domain.flag.SwimFlagKnowledge;
import com.beachassistant.domain.model.BeachDecision;
import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.web.dto.JellyfishDto;
import com.beachassistant.web.dto.LifeguardHoursDto;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Russian-language Telegram messages. Main beach status uses {@link StatusCardTemplate};
 * secondary details use {@link #formatStatusDetails(BeachDecision, BeachProfile)}.
 */
@Component
public class ResponseFormatter {

    private final WeatherComfortEvaluator weatherComfortEvaluator;
    private final StatusCardModelMapper statusCardModelMapper;

    private static final Map<ReasonCode, String> REASON_RU = Map.of(
            ReasonCode.SEA_RISK_HIGH, "повышенное волнение на море",
            ReasonCode.SEA_RISK_SEVERE, "опасные условия на море",
            ReasonCode.HEALTH_ADVISORY_ACTIVE, "официальное предупреждение",
            ReasonCode.LIFEGUARDS_OFF_DUTY, "спасатели не дежурят",
            ReasonCode.NO_FRESH_DATA, "нет актуальных данных",
            ReasonCode.JELLYFISH_REPORTS_HIGH, "зафиксированы медузы",
            ReasonCode.BEACH_TEMPORARILY_CLOSED, "пляж временно закрыт",
            ReasonCode.SOURCE_CONFLICT, "расхождение данных из источников"
    );

    public ResponseFormatter(WeatherComfortEvaluator weatherComfortEvaluator,
                             StatusCardModelMapper statusCardModelMapper) {
        this.weatherComfortEvaluator = weatherComfortEvaluator;
        this.statusCardModelMapper = statusCardModelMapper;
    }

    public String formatStatus(BeachDecision decision, BeachProfile profile, boolean hasCamera) {
        StatusCardModel model = statusCardModelMapper.toModel(decision, profile, hasCamera);
        return StatusCardTemplate.format(model);
    }

    /**
     * Secondary view: comfort, forecast window, source transparency, flag legend, profile notes — not the main card.
     */
    public String formatStatusDetails(BeachDecision decision, BeachProfile profile) {
        StringBuilder sb = new StringBuilder();
        sb.append(LegendSection.DETAILS_EMOJI).append(" Подробнее — ").append(decision.getBeachDisplayName())
                .append(" (").append(decision.getCity()).append(")\n\n");

        sb.append(LegendSection.WEATHER_EMOJI).append(" Дополнительно\n");
        if (decision.getRelativeHumidityPct() != null) {
            sb.append(LegendSection.HUMIDITY_EMOJI).append(" Влажность: ").append(formatNumber(decision.getRelativeHumidityPct(), "%")).append("\n");
        }
        sb.append("Комфорт: ").append(weatherComfortEvaluator.comfortLabel(decision)).append("\n");

        if (!decision.getReasonCodes().isEmpty()) {
            sb.append("Факторы: ")
                    .append(decision.getReasonCodes().stream()
                            .map(r -> REASON_RU.getOrDefault(r, r.name().toLowerCase()))
                            .collect(Collectors.joining(", ")))
                    .append("\n");
        }

        if (decision.getEffectiveFrom() != null && decision.getEffectiveTo() != null) {
            sb.append("\n").append(LegendSection.CLOCK_EMOJI).append(" Окно прогноза (волна и вода)\n");
            sb.append("с ").append(TimeUtil.formatForDisplay(decision.getEffectiveFrom())).append("\n");
            sb.append("по ").append(TimeUtil.formatForDisplay(decision.getEffectiveTo())).append("\n");
            if (decision.isIntervalIsInferred()) {
                sb.append("Границы интервала оценены по часовому прогнозу.\n");
            }
        }

        sb.append("\n").append(LegendSection.SOURCE_EMOJI).append(" Источники\n");
        for (String sourceLine : sourceLines(decision)) {
            sb.append("• ").append(sourceLine).append("\n");
        }

        appendBeachProfileDetails(sb, profile);

        sb.append("\n").append(LegendSection.DETAILS_EMOJI).append(" Справка по флагам\n");
        sb.append(SwimFlagKnowledge.compactLegendRu()).append("\n");

        sb.append("\nУверенность: ").append(confidenceRu(decision)).append("\n");
        sb.append("Сводка сформирована: ").append(TimeUtil.formatForDisplay(decision.getGeneratedAt()))
                .append("\n");

        return sb.toString();
    }

    private void appendBeachProfileDetails(StringBuilder sb, BeachProfile profile) {
        if (profile == null || profile.isEmpty()) {
            return;
        }
        sb.append("\n").append(LegendSection.INFO_EMOJI).append(" О пляже\n");
        if (profile.description() != null && !profile.description().isBlank()) {
            sb.append(profile.description()).append("\n");
        }
        if (profile.accessibilityNotes() != null && !profile.accessibilityNotes().isBlank()) {
            sb.append("Доступность: ").append(profile.accessibilityNotes()).append("\n");
        }
        if (profile.parkingNotes() != null && !profile.parkingNotes().isBlank()) {
            sb.append("Парковка: ").append(profile.parkingNotes()).append("\n");
        }
        if (profile.notes() != null && !profile.notes().isBlank()) {
            sb.append("Заметки: ").append(profile.notes()).append("\n");
        }
        if (profile.lifeguardNotes() != null && !profile.lifeguardNotes().isBlank()) {
            sb.append("Спасатели: ").append(profile.lifeguardNotes()).append("\n");
        }
        if (profile.waterQualityPlaceholder() != null && !profile.waterQualityPlaceholder().isBlank()) {
            sb.append("Качество воды: ").append(profile.waterQualityPlaceholder()).append("\n");
        }
        if (profile.jellyfishPlaceholder() != null && !profile.jellyfishPlaceholder().isBlank()) {
            sb.append("Медузы: ").append(profile.jellyfishPlaceholder()).append("\n");
        }
    }

    public String formatHours(LifeguardHoursDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(LegendSection.LIFEGUARD_EMOJI).append(" Спасатели — ").append(dto.getBeach()).append("\n\n");

        if (dto.isOnDuty()) {
            sb.append("✅ Дежурят до ").append(dto.getCloseTime()).append("\n");
        } else {
            sb.append("❌ Сейчас не дежурят\n");
            if (dto.getOpenTime() != null) {
                sb.append("Расписание: ").append(dto.getOpenTime()).append(" — ").append(dto.getCloseTime()).append("\n");
            }
        }

        appendFreshnessNote(sb, dto.getFreshnessStatus(), dto.getCapturedAt());
        return sb.toString();
    }

    public String formatJellyfish(JellyfishDto dto) {
        StringBuilder sb = new StringBuilder();
        sb.append(LegendSection.JELLYFISH_EMOJI).append(" Медузы — ").append(dto.getBeach()).append("\n\n");

        sb.append("Уровень: ").append(jellyfishSeverityRu(dto)).append("\n");

        if (dto.getReportCount() != null && dto.getReportCount() > 0) {
            sb.append("Сообщений: ").append(dto.getReportCount()).append("\n");
        }

        if (dto.getWindowStart() != null && dto.getWindowEnd() != null) {
            sb.append("Период: ")
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
            return LegendSection.CAMERA_EMOJI + " Камера — " + beachName + "\n\n⚠ Камера временно недоступна (источник не отвечает).";
        }
        return LegendSection.CAMERA_EMOJI + " Камера — " + beachName + "\n\n🔴 Прямая трансляция:\n" + liveUrl;
    }

    public String formatCameraUnavailable(String beachName) {
        return LegendSection.CAMERA_EMOJI + " " + beachName + "\n\nКамера не настроена для этого пляжа.";
    }

    public String formatCameraTemporarilyUnavailable(String beachName) {
        return LegendSection.CAMERA_EMOJI + " Камера — " + beachName
                + "\n\n⚠ Сейчас трансляция недоступна на стороне провайдера."
                + "\nПопробуйте позже или выберите другой пляж.";
    }

    public String formatBeachList(java.util.List<com.beachassistant.persistence.entity.BeachEntity> beaches) {
        String city = beaches.isEmpty() ? "" : beaches.get(0).getCity().getName();
        StringBuilder sb = new StringBuilder(LegendSection.BEACH_EMOJI).append(" Поддерживаемые пляжи (").append(city).append("):\n\n");
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
        sb.append("\nКоманды: /status, /details, /hours, /jellyfish, /live, /cam");
        return sb.toString();
    }

    public String formatWelcome(java.util.List<com.beachassistant.persistence.entity.BeachEntity> beaches) {
        return LegendSection.WAVE_HI + " Привет! Я Beach Assistant — помогу узнать, можно ли идти на пляж прямо сейчас.\n\n"
                + "Поддерживаемые пляжи: " + beaches.stream()
                .map(com.beachassistant.persistence.entity.BeachEntity::getDisplayName)
                .collect(java.util.stream.Collectors.joining(", "))
                + "\n\nКоманды:\n"
                + "/beaches — список пляжей\n"
                + "/status <пляж> — статус пляжа\n"
                + "/details <пляж> — подробности\n"
                + "/hours <пляж> — спасатели\n"
                + "/jellyfish <пляж> — медузы\n"
                + "/live <пляж> — камера (ссылка)\n"
                + "/cam <пляж> — снимок с камеры\n";
    }

    private String confidenceRu(BeachDecision d) {
        return switch (d.getConfidence()) {
            case HIGH -> "высокая";
            case MEDIUM -> "средняя";
            case LOW -> "низкая";
        };
    }

    private String jellyfishSeverityRu(JellyfishDto dto) {
        boolean noFreshData = dto.getFreshnessStatus() == FreshnessStatus.EXPIRED
                || (dto.getFreshnessStatus() == FreshnessStatus.STALE
                && (dto.getCapturedAt() == null
                || dto.getReportCount() == null
                || dto.getReportCount() == 0));
        if (dto.getSeverityLevel() == null || noFreshData) {
            return "неизвестно (нет свежих наблюдений)";
        }
        return switch (dto.getSeverityLevel()) {
            case NONE -> "не обнаружены";
            case LOW -> "единичные случаи";
            case MEDIUM -> "умеренное присутствие";
            case HIGH -> "высокое присутствие";
        };
    }

    private void appendFreshnessNote(StringBuilder sb, FreshnessStatus freshness,
                                     java.time.ZonedDateTime capturedAt) {
        if (freshness == FreshnessStatus.STALE) {
            sb.append("\nДанные частично устарели. Последнее обновление: ")
                    .append(TimeUtil.formatForDisplay(capturedAt)).append("\n");
        } else if (freshness == FreshnessStatus.EXPIRED) {
            if (capturedAt == null) {
                sb.append("\nНет свежих наблюдений по этому пляжу.\n");
            } else {
                sb.append("\nДанные устарели. Последнее обновление: ")
                        .append(TimeUtil.formatForDisplay(capturedAt)).append("\n");
            }
        }
    }

    private List<String> sourceLines(BeachDecision decision) {
        List<String> lines = new ArrayList<>();
        lines.add(sourceLine(decision, SourceType.SEA_FORECAST, LegendSection.SEA_EMOJI + " Море и ветер — Open-Meteo Marine/Forecast"));
        lines.add(sourceLine(decision, SourceType.HEALTH_ADVISORY, LegendSection.AIR_EMOJI + " Воздух — Open-Meteo Air Quality (CAMS)"));
        lines.add(sourceLine(decision, SourceType.LIFEGUARD_SCHEDULE, LegendSection.LIFEGUARD_EMOJI + " Спасатели — расписание в базе"));
        lines.add(sourceLine(decision, SourceType.JELLYFISH, LegendSection.JELLYFISH_EMOJI + " Медузы — iNaturalist"));
        return lines;
    }

    private String sourceLine(BeachDecision decision, SourceType type, String label) {
        if (decision.getMissingSourceTypes().contains(type)) {
            return label + " — нет данных";
        }
        FreshnessStatus f = decision.getSourceFreshness() != null ? decision.getSourceFreshness().get(type) : null;
        String freshnessWord = switch (f != null ? f : FreshnessStatus.EXPIRED) {
            case FRESH -> "свежие";
            case STALE -> "частично устарели";
            case EXPIRED -> "устарели";
        };
        ZonedDateTime cap = decision.getSourceCapturedAt() != null
                ? decision.getSourceCapturedAt().get(type)
                : null;
        String timePart = cap != null
                ? "обновлено " + formatAge(cap) + " назад (" + TimeUtil.formatForDisplay(cap) + ")"
                : "время записи неизвестно";
        return label + " — " + freshnessWord + ", " + timePart;
    }

    private String formatAge(ZonedDateTime capturedAt) {
        Duration age = Duration.between(capturedAt, TimeUtil.nowInIsrael());
        long minutes = Math.max(0, age.toMinutes());
        if (minutes < 60) {
            return minutes + " мин";
        }
        long hours = minutes / 60;
        long remMinutes = minutes % 60;
        if (remMinutes == 0) {
            return hours + " ч";
        }
        return hours + " ч " + remMinutes + " мин";
    }

    private String formatNumber(Double value, String unit) {
        if (value == null) {
            return "—";
        }
        return String.format(java.util.Locale.US, "%.1f", value).replace(".0", "") + unit;
    }
}
