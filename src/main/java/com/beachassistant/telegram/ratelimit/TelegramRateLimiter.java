package com.beachassistant.telegram.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-chat token bucket. Sustains {@code requestsPerMinute} with capacity {@code burst}.
 * Thread-safe via CAS on each bucket; buckets evicted after {@code bucketIdleTtl}.
 */
@Slf4j
@Component
public class TelegramRateLimiter {

    private final TelegramRateLimitProperties properties;
    private final Clock clock;
    private final Cache<Long, Bucket> buckets;
    private final Cache<Long, Long> lastCooldownReplyAt;
    private final double refillTokensPerMilli;

    public TelegramRateLimiter(TelegramRateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(properties.getMaxTrackedChats())
                .expireAfterAccess(properties.getBucketIdleTtl())
                .build();
        this.lastCooldownReplyAt = Caffeine.newBuilder()
                .maximumSize(properties.getMaxTrackedChats())
                .expireAfterWrite(properties.getBucketIdleTtl())
                .build();
        double perMinute = Math.max(1, properties.getRequestsPerMinute());
        this.refillTokensPerMilli = perMinute / 60_000.0;
    }

    public Decision tryAcquire(long chatId) {
        if (!properties.isEnabled()) {
            return Decision.grant();
        }
        Bucket bucket = buckets.get(chatId, id -> new Bucket(properties.getBurst(), clock.millis()));
        long now = clock.millis();
        boolean granted = bucket.tryConsume(now, properties.getBurst(), refillTokensPerMilli);
        if (granted) {
            return Decision.grant();
        }
        boolean shouldReply = shouldReplyCooldown(chatId, now);
        return Decision.deny(shouldReply);
    }

    private boolean shouldReplyCooldown(long chatId, long now) {
        long intervalMs = Math.max(1_000L, properties.getCooldownReplyInterval().toMillis());
        Long previous = lastCooldownReplyAt.getIfPresent(chatId);
        if (previous != null && now - previous < intervalMs) {
            return false;
        }
        lastCooldownReplyAt.put(chatId, now);
        return true;
    }

    public record Decision(boolean allowed, boolean shouldSendCooldownReply) {
        public static Decision grant() {
            return new Decision(true, false);
        }

        public static Decision deny(boolean shouldReply) {
            return new Decision(false, shouldReply);
        }
    }

    /**
     * Token bucket. Fractional tokens stored as fixed-point millis * rate for precision; here simplified as double.
     */
    private static final class Bucket {
        private final AtomicLong state = new AtomicLong();

        private Bucket(int capacity, long nowMillis) {
            state.set(pack(capacity * 1000L, nowMillis));
        }

        boolean tryConsume(long now, int capacity, double refillPerMilli) {
            while (true) {
                long current = state.get();
                long tokensMilli = unpackTokens(current);
                long lastRefill = unpackTs(current);
                long elapsed = Math.max(0L, now - lastRefill);
                long newTokensMilli = Math.min(capacity * 1000L,
                        tokensMilli + (long) (elapsed * refillPerMilli * 1000L));
                if (newTokensMilli < 1000L) {
                    long updated = pack(newTokensMilli, now);
                    state.compareAndSet(current, updated);
                    return false;
                }
                long afterConsume = newTokensMilli - 1000L;
                long updated = pack(afterConsume, now);
                if (state.compareAndSet(current, updated)) {
                    return true;
                }
            }
        }

        private static long pack(long tokensMilli, long ts) {
            // 20 bits for tokensMilli (<= 1,048,575 ~ 1000 tokens), 44 bits for ts millis.
            long clampedTokens = Math.min(0xFFFFFL, Math.max(0L, tokensMilli));
            long clampedTs = ts & 0xFFFFFFFFFFFL;
            return (clampedTokens << 44) | clampedTs;
        }

        private static long unpackTokens(long packed) {
            return (packed >>> 44) & 0xFFFFFL;
        }

        private static long unpackTs(long packed) {
            return packed & 0xFFFFFFFFFFFL;
        }
    }
}
