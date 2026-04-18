package com.beachassistant.telegram.formatter;

import com.beachassistant.common.enums.FreshnessStatus;
import com.beachassistant.common.enums.Recommendation;

/**
 * Single source of truth for user-facing emoji used in Telegram cards.
 * Centralizing these prevents drift between status cards, keyboards, and error messages.
 */
public final class LegendSection {

    // Recommendation
    public static final String OK_EMOJI = "✅";
    public static final String CAUTION_EMOJI = "⚠️";
    public static final String DANGER_EMOJI = "🚫";
    public static final String UNKNOWN_EMOJI = "❓";

    // Freshness dots (match common traffic-light intuition)
    public static final String FRESH_DOT = "🟢";
    public static final String STALE_DOT = "🟡";
    public static final String EXPIRED_DOT = "🟠";

    // Section icons
    public static final String DETAILS_EMOJI = "📋";
    public static final String WEATHER_EMOJI = "🌤";
    public static final String HUMIDITY_EMOJI = "💧";
    public static final String CLOCK_EMOJI = "⏱";
    public static final String SOURCE_EMOJI = "📡";
    public static final String INFO_EMOJI = "ℹ️";

    // Domain icons
    public static final String SEA_EMOJI = "🌊";
    public static final String AIR_EMOJI = "🏥";
    public static final String LIFEGUARD_EMOJI = "🏊";
    public static final String JELLYFISH_EMOJI = "🪼";
    public static final String CAMERA_EMOJI = "📷";
    public static final String BEACH_EMOJI = "🏖";
    public static final String WAVE_EMOJI = "🌊";

    // Livestream / status indicators
    public static final String LIVE_EMOJI = "🔴";

    // Action/system
    public static final String HOURGLASS_EMOJI = "⏳";
    public static final String WAVE_HI = "👋";

    private LegendSection() {
    }

    public static String recommendation(Recommendation r) {
        return switch (r) {
            case CAN_SWIM -> OK_EMOJI;
            case CAUTION -> CAUTION_EMOJI;
            case DO_NOT_RECOMMEND -> DANGER_EMOJI;
            case UNKNOWN -> UNKNOWN_EMOJI;
        };
    }

    public static String freshnessDot(FreshnessStatus f) {
        if (f == null) {
            return EXPIRED_DOT;
        }
        return switch (f) {
            case FRESH -> FRESH_DOT;
            case STALE -> STALE_DOT;
            case EXPIRED -> EXPIRED_DOT;
        };
    }

    public static String freshnessLabel(FreshnessStatus f) {
        if (f == null) {
            return "устарело";
        }
        return switch (f) {
            case FRESH -> "свежо";
            case STALE -> "частично устарело";
            case EXPIRED -> "устарело";
        };
    }
}
