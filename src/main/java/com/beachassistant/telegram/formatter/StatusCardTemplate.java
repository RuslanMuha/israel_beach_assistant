package com.beachassistant.telegram.formatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link StatusCardModel} into the compact Telegram status card layout.
 * Presentation-only: no domain rules.
 */
public final class StatusCardTemplate {

    private StatusCardTemplate() {
    }

    public static String format(StatusCardModel m) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.beachName()).append(" (").append(m.city()).append(")\n");
        sb.append(m.overallEmoji()).append(" ").append(m.overallLabel());
        if (m.freshnessBadge() != null && !m.freshnessBadge().isBlank()) {
            sb.append("  ").append(m.freshnessBadge());
        }
        sb.append("\n");
        sb.append(m.shortHumanRecommendation()).append("\n\n");

        appendSection(sb, "Сейчас:", listNowBullets(m));
        appendSection(sb, "Условия:", listConditionsBullets(m));
        appendSection(sb, "Пляж:", listBeachBullets(m));

        sb.append("Обновлено: ").append(m.updatedAtLine()).append("\n");
        m.sourceSummaryLine().ifPresent(line -> sb.append(line).append("\n"));
        return sb.toString().trim();
    }

    private static List<String> listNowBullets(StatusCardModel m) {
        List<String> bullets = new ArrayList<>();
        addBullet(bullets, "Флаг", m.flagLabel());
        addBullet(bullets, "Спасатели", m.lifeguardLine());
        addBullet(bullets, "Волна", m.waveLine());
        addBullet(bullets, "Вода", m.waterLine());
        return bullets;
    }

    private static List<String> listConditionsBullets(StatusCardModel m) {
        List<String> bullets = new ArrayList<>();
        addBullet(bullets, "Воздух", m.airLine());
        addBullet(bullets, "Ветер", m.windLine());
        addBullet(bullets, "UV", m.uvLine());
        return bullets;
    }

    private static List<String> listBeachBullets(StatusCardModel m) {
        List<String> bullets = new ArrayList<>();
        addBullet(bullets, "Тип", m.beachTypesLine());
        addBullet(bullets, "Удобства", m.facilitiesLine());
        addBullet(bullets, "Камера", m.cameraLine());
        return bullets;
    }

    private static void addBullet(List<String> bullets, String label, String value) {
        if (value != null && !value.isBlank()) {
            bullets.add(label + ": " + value.trim());
        }
    }

    private static void appendSection(StringBuilder sb, String title, List<String> bullets) {
        if (bullets.isEmpty()) {
            return;
        }
        sb.append(title).append("\n");
        for (String b : bullets) {
            sb.append("- ").append(b).append("\n");
        }
        sb.append("\n");
    }
}
