package com.beachassistant.telegram.outbox;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "beach.telegram.outbox")
@Getter
@Setter
public class TelegramOutboxProperties {

    /** When false, {@link TelegramSender} bypasses persistence and sends synchronously. */
    private boolean enabled = true;

    /** Max rows the dispatcher claims per tick. */
    private int batchSize = 10;

    /**
     * Milliseconds between dispatcher ticks. Used by {@code @Scheduled}; must be a plain number
     * (not {@code 500ms}) so {@code fixedDelayString} can parse it.
     */
    private long pollIntervalMillis = 500;

    /** Attempts before a message is marked FAILED. */
    private int maxAttempts = 8;

    /** Backoff for generic transient errors; doubles each attempt, capped at {@link #maxBackoff}. */
    private Duration initialBackoff = Duration.ofSeconds(2);

    private Duration maxBackoff = Duration.ofMinutes(5);

    /** Rows older than this are purged from the table after completion. */
    private Duration retentionAfterSent = Duration.ofDays(3);

    /** FAILED rows older than this are purged (kept longer for diagnosis). */
    private Duration retentionFailed = Duration.ofDays(14);
}
