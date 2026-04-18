package com.beachassistant.telegram.formatter;

import java.util.Optional;

/**
 * Telegram main status card: presentation-oriented fields for {@link StatusCardTemplate}.
 * Null fields mean the corresponding line is omitted.
 */
public record StatusCardModel(
        String beachName,
        String city,
        String overallEmoji,
        String overallLabel,
        String freshnessBadge,
        String shortHumanRecommendation,
        String flagLabel,
        String lifeguardLine,
        String waveLine,
        String waterLine,
        String airLine,
        String windLine,
        String uvLine,
        String beachTypesLine,
        String facilitiesLine,
        String cameraLine,
        String updatedAtLine,
        Optional<String> sourceSummaryLine
) {
}
