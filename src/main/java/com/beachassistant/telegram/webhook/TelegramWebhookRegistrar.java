package com.beachassistant.telegram.webhook;

import com.beachassistant.config.TelegramProperties;
import com.beachassistant.telegram.client.TelegramBotSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.telegram.telegrambots.meta.api.methods.updates.SetWebhook;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

/**
 * Registers the bot webhook with Telegram when configured.
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "beach.telegram", name = "mode", havingValue = "webhook")
public class TelegramWebhookRegistrar implements ApplicationRunner, Ordered {

    private final TelegramProperties telegramProperties;
    private final TelegramBotSender telegramBotSender;

    @Override
    public void run(ApplicationArguments args) {
        if (!telegramProperties.isWebhookAutoRegister()) {
            log.info("Telegram webhook auto-registration is disabled; configure webhook manually with Bot API setWebhook");
            return;
        }
        String webhookUrl = TelegramWebhookUrlComposer.compose(telegramProperties);
        SetWebhook.SetWebhookBuilder builder = SetWebhook.builder().url(webhookUrl);
        if (StringUtils.hasText(telegramProperties.getWebhookSecretToken())) {
            builder.secretToken(telegramProperties.getWebhookSecretToken().trim());
        }
        SetWebhook setWebhook = builder.build();
        try {
            Boolean ok = telegramBotSender.execute(setWebhook);
            if (Boolean.TRUE.equals(ok)) {
                log.info("Telegram setWebhook succeeded for URL {}", webhookUrl);
                if (StringUtils.hasText(telegramProperties.getWebhookSecretToken())) {
                    log.info("Telegram webhook requests will require X-Telegram-Bot-Api-Secret-Token header validation");
                }
            } else {
                log.warn("Telegram setWebhook returned non-true result for URL {}", webhookUrl);
            }
        } catch (TelegramApiException e) {
            log.error("Telegram setWebhook failed: {}", e.getMessage(), e);
            throw new IllegalStateException("Failed to register Telegram webhook", e);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 20;
    }
}
