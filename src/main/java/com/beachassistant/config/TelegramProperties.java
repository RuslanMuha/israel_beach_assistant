package com.beachassistant.config;

import com.beachassistant.common.enums.TelegramMode;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "beach.telegram")
@Getter
@Setter
public class TelegramProperties {

    /**
     * How updates are received: long polling (default) or HTTPS webhook.
     */
    private TelegramMode mode = TelegramMode.POLLING;

    /**
     * Public HTTPS base URL for webhook registration (no trailing slash), e.g. https://myapp.onrender.com
     */
    private String publicBaseUrl = "";

    /**
     * Path segment for the webhook HTTP endpoint (must start with /).
     */
    private String webhookPath = "/api/telegram/webhook";

    /**
     * When true and mode is webhook, call setWebhook on startup using publicBaseUrl + webhookPath.
     */
    private boolean webhookAutoRegister = true;

    /**
     * Optional secret for Telegram's webhook secret_token; if set, incoming POSTs must include
     * header X-Telegram-Bot-Api-Secret-Token with the same value.
     */
    private String webhookSecretToken = "";

    private String token = "CONFIGURE_ME";
    private String username = "beach_assistant_bot";

    /**
     * Opt-in UI features that depend on downstream subsystems still under rollout.
     */
    private Features features = new Features();

    public boolean isPlaceholderToken() {
        return token == null || token.isBlank() || "CONFIGURE_ME".equalsIgnoreCase(token.trim());
    }

    @Getter
    @Setter
    public static class Features {
        /** Shows the "Подписаться" inline button and enables subscription commands. */
        private boolean subscriptionsEnabled = false;
    }
}
