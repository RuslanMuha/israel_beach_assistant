package com.beachassistant.telegram.client;

import com.beachassistant.config.TelegramProperties;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.DefaultAbsSender;
import org.telegram.telegrambots.bots.DefaultBotOptions;

/**
 * Outbound Telegram Bot API calls (sendMessage, setWebhook, etc.) using the configured bot token.
 * Shared by long polling and webhook transports.
 */
@Component
public class TelegramBotSender extends DefaultAbsSender {

    public TelegramBotSender(TelegramProperties telegramProperties) {
        super(new DefaultBotOptions(), telegramProperties.getToken());
    }
}
