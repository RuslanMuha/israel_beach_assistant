package com.beachassistant.telegram.webhook;

import com.beachassistant.common.enums.TelegramMode;
import com.beachassistant.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * Fails fast when webhook mode is enabled but required configuration is missing.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "beach.telegram", name = "mode", havingValue = "webhook")
public class TelegramWebhookStartupValidator implements ApplicationRunner, Ordered {

    private final TelegramProperties telegramProperties;

    @Override
    public void run(ApplicationArguments args) {
        if (telegramProperties.getMode() != TelegramMode.WEBHOOK) {
            return;
        }
        if (telegramProperties.isPlaceholderToken()) {
            throw new IllegalStateException(
                    "beach.telegram.token (e.g. TELEGRAM_BOT_TOKEN) must be set to a real bot token when beach.telegram.mode=webhook");
        }
        if (telegramProperties.isWebhookAutoRegister()) {
            if (telegramProperties.getPublicBaseUrl() == null
                    || telegramProperties.getPublicBaseUrl().isBlank()) {
                throw new IllegalStateException(
                        "beach.telegram.public-base-url (e.g. TELEGRAM_PUBLIC_BASE_URL) is required when "
                                + "beach.telegram.webhook-auto-register is true");
            }
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
