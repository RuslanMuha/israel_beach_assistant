package com.beachassistant.telegram.webhook;

import com.beachassistant.config.TelegramProperties;

/**
 * Builds the full HTTPS URL passed to Telegram setWebhook.
 */
public final class TelegramWebhookUrlComposer {

    private TelegramWebhookUrlComposer() {
    }

    public static String compose(TelegramProperties properties) {
        String base = properties.getPublicBaseUrl().trim();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String path = properties.getWebhookPath();
        if (path == null || path.isBlank()) {
            path = "/api/telegram/webhook";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        return base + path;
    }
}
