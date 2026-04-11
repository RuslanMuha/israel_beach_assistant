package com.beachassistant.common.enums;

/**
 * How the app receives Telegram updates. Only one mode should be active at a time.
 */
public enum TelegramMode {
    POLLING,
    WEBHOOK
}
