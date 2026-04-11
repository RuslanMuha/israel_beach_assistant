package com.beachassistant.telegram.handler;

import com.beachassistant.config.TelegramProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Long-polling transport: forwards Telegram updates to {@link BeachBotUpdateProcessor}.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(prefix = "beach.telegram", name = "mode", havingValue = "polling", matchIfMissing = true)
public class BeachLongPollingBot extends TelegramLongPollingBot {

    private final TelegramProperties telegramProperties;
    private final BeachBotUpdateProcessor updateProcessor;

    public BeachLongPollingBot(TelegramProperties telegramProperties,
                               BeachBotUpdateProcessor updateProcessor) {
        super(telegramProperties.getToken());
        this.telegramProperties = telegramProperties;
        this.updateProcessor = updateProcessor;
    }

    @Override
    public String getBotUsername() {
        return telegramProperties.getUsername();
    }

    @Override
    public void onUpdateReceived(Update update) {
        updateProcessor.processUpdate(update);
    }
}
