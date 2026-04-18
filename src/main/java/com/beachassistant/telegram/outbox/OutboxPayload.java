package com.beachassistant.telegram.outbox;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Serialized message payload. Stored as JSON in {@code telegram_outbox.payload_json}.
 * Nullable fields are omitted when serialized; dispatcher picks the right Telegram method
 * based on {@link OutboxMessageType}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OutboxPayload(
        String text,
        String parseMode,
        JsonNode replyMarkup,
        String photoUrl,
        String caption,
        String chatAction
) {

    public static OutboxPayload text(String text, String parseMode, JsonNode replyMarkup) {
        return new OutboxPayload(text, parseMode, replyMarkup, null, null, null);
    }

    public static OutboxPayload photo(String photoUrl, String caption, JsonNode replyMarkup) {
        return new OutboxPayload(null, null, replyMarkup, photoUrl, caption, null);
    }

    public static OutboxPayload chatAction(String action) {
        return new OutboxPayload(null, null, null, null, null, action);
    }
}
