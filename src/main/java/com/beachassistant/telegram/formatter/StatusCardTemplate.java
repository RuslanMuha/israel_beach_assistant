package com.beachassistant.telegram.formatter;

import com.beachassistant.i18n.I18n;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders {@link StatusCardModel} into the compact Telegram status card layout.
 * Presentation-only: no domain rules.
 */
public final class StatusCardTemplate {

    private StatusCardTemplate() {
    }

    public static String format(StatusCardModel m, I18n i18n) {
        StringBuilder sb = new StringBuilder();
        sb.append(m.beachName()).append(" (").append(m.city()).append(")\n");
        sb.append(m.overallEmoji()).append(" ").append(m.overallLabel());
        if (m.freshnessBadge() != null && !m.freshnessBadge().isBlank()) {
            sb.append("  ").append(m.freshnessBadge());
        }
        sb.append("\n");
        sb.append(m.shortHumanRecommendation()).append("\n\n");

        appendSection(sb, i18n.t("card.section.now"), listNowBullets(m, i18n));
        appendSection(sb, i18n.t("card.section.conditions"), listConditionsBullets(m, i18n));
        appendSection(sb, i18n.t("card.section.beach"), listBeachBullets(m, i18n));

        sb.append(i18n.t("card.updated", m.updatedAtLine())).append("\n");
        m.sourceSummaryLine().ifPresent(line -> sb.append(line).append("\n"));
        return sb.toString().trim();
    }

    private static List<String> listNowBullets(StatusCardModel m, I18n i18n) {
        List<String> bullets = new ArrayList<>();
        addBullet(bullets, i18n.t("card.bullet.flag"), m.flagLabel());
        addBullet(bullets, i18n.t("card.bullet.lifeguards"), m.lifeguardLine());
        addBullet(bullets, i18n.t("card.bullet.wave"), m.waveLine());
        addBullet(bullets, i18n.t("card.bullet.water"), m.waterLine());
        return bullets;
    }

    private static List<String> listConditionsBullets(StatusCardModel m, I18n i18n) {
        List<String> bullets = new ArrayList<>();
        addBullet(bullets, i18n.t("card.bullet.air"), m.airLine());
        addBullet(bullets, i18n.t("card.bullet.wind"), m.windLine());
        addBullet(bullets, i18n.t("card.bullet.uv"), m.uvLine());
        return bullets;
    }

    private static List<String> listBeachBullets(StatusCardModel m, I18n i18n) {
        List<String> bullets = new ArrayList<>();
        addBullet(bullets, i18n.t("card.bullet.type"), m.beachTypesLine());
        addBullet(bullets, i18n.t("card.bullet.facilities"), m.facilitiesLine());
        addBullet(bullets, i18n.t("card.bullet.camera"), m.cameraLine());
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
