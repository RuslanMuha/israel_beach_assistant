package com.beachassistant.telegram;

import com.beachassistant.common.enums.TelegramMode;
import com.beachassistant.config.TelegramProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Startup log line for which Telegram transport is active (no secrets).
 */
@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class TelegramTransportLoggingRunner implements ApplicationRunner {

    private final TelegramProperties telegramProperties;

    @Override
    public void run(ApplicationArguments args) {
        TelegramMode mode = telegramProperties.getMode();
        log.info("Telegram transport mode: {}", mode);
        if (mode == TelegramMode.WEBHOOK) {
            log.info("Telegram webhook HTTP path: {}", telegramProperties.getWebhookPath());
        } else {
            log.info("Telegram updates: long polling (getUpdates)");
        }
    }
}
