package com.beachassistant.config;

import com.beachassistant.telegram.handler.BeachLongPollingBot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

@Slf4j
@Configuration
@Profile("!test")
@ConditionalOnProperty(prefix = "beach.telegram", name = "mode", havingValue = "polling", matchIfMissing = true)
public class TelegramBotRegistrationConfig {

    @Bean
    ApplicationRunner registerTelegramBot(BeachLongPollingBot botHandler) {
        return args -> {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(botHandler);
            log.info("Telegram long-polling bot registered: @{}", botHandler.getBotUsername());
        };
    }
}
