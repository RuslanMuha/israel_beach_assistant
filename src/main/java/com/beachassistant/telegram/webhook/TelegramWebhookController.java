package com.beachassistant.telegram.webhook;

import com.beachassistant.config.TelegramProperties;
import com.beachassistant.telegram.handler.BeachBotUpdateProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * HTTPS endpoint that receives Telegram updates in webhook mode.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "beach.telegram", name = "mode", havingValue = "webhook")
public class TelegramWebhookController {

    public static final String SECRET_TOKEN_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramProperties telegramProperties;
    private final BeachBotUpdateProcessor beachBotUpdateProcessor;

    @PostMapping(path = "${beach.telegram.webhook-path:/api/telegram/webhook}",
            consumes = "application/json")
    public ResponseEntity<Void> handleWebhook(
            @RequestBody Update update,
            @RequestHeader(value = SECRET_TOKEN_HEADER, required = false) String secretHeader) {
        if (!validateSecretIfConfigured(secretHeader)) {
            log.warn("Rejected Telegram webhook request: invalid or missing secret token header");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        beachBotUpdateProcessor.processUpdate(update);
        return ResponseEntity.ok().build();
    }

    private boolean validateSecretIfConfigured(String secretHeader) {
        String expected = telegramProperties.getWebhookSecretToken();
        if (!StringUtils.hasText(expected)) {
            return true;
        }
        return expected.trim().equals(secretHeader);
    }
}
