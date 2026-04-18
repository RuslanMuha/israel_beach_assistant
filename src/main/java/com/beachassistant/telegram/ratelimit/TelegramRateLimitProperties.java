package com.beachassistant.telegram.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "beach.telegram.rate-limit")
@Getter
@Setter
public class TelegramRateLimitProperties {

    private boolean enabled = true;

    /** Sustained rate per chat in requests per minute. */
    private int requestsPerMinute = 20;

    /** Maximum burst size (bucket capacity). */
    private int burst = 5;

    /** Single-flight wait hint; chats exceeding it see a cooldown reply. */
    private Duration cooldownReplyInterval = Duration.ofSeconds(30);

    /** Maximum number of tracked chat buckets (memory safety). */
    private int maxTrackedChats = 10_000;

    /** How long an idle chat bucket is kept before eviction. */
    private Duration bucketIdleTtl = Duration.ofHours(1);
}
