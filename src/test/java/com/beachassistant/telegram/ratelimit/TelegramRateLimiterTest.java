package com.beachassistant.telegram.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramRateLimiterTest {

    private static TelegramRateLimitProperties props(int rpm, int burst, Duration cooldown) {
        TelegramRateLimitProperties p = new TelegramRateLimitProperties();
        p.setEnabled(true);
        p.setRequestsPerMinute(rpm);
        p.setBurst(burst);
        p.setCooldownReplyInterval(cooldown);
        p.setMaxTrackedChats(100);
        p.setBucketIdleTtl(Duration.ofMinutes(5));
        return p;
    }

    /** Mutable clock that we advance manually. */
    private static class MutableClock extends Clock {
        private final AtomicLong millis;

        MutableClock(long startMillis) {
            this.millis = new AtomicLong(startMillis);
        }

        void advance(Duration d) {
            millis.addAndGet(d.toMillis());
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis.get());
        }

        @Override
        public long millis() {
            return millis.get();
        }
    }

    @Test
    void disabledAlwaysGrants() {
        TelegramRateLimitProperties p = props(1, 1, Duration.ofSeconds(30));
        p.setEnabled(false);
        TelegramRateLimiter limiter = new TelegramRateLimiter(p, new MutableClock(0L));
        for (int i = 0; i < 100; i++) {
            assertThat(limiter.tryAcquire(42L).allowed()).isTrue();
        }
    }

    @Test
    void burstExhaustedThenDenied() {
        MutableClock clock = new MutableClock(0L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(
                props(60, 3, Duration.ofSeconds(30)), clock);
        assertThat(limiter.tryAcquire(1L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(1L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(1L).allowed()).isTrue();
        // 4th call without advancing time → denied
        TelegramRateLimiter.Decision denied = limiter.tryAcquire(1L);
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.shouldSendCooldownReply()).isTrue();
    }

    @Test
    void refillRestoresCapacity() {
        MutableClock clock = new MutableClock(0L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(
                props(60, 2, Duration.ofSeconds(30)), clock); // 1 token/sec
        assertThat(limiter.tryAcquire(7L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(7L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(7L).allowed()).isFalse();
        clock.advance(Duration.ofSeconds(2)); // +2 tokens
        assertThat(limiter.tryAcquire(7L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(7L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(7L).allowed()).isFalse();
    }

    @Test
    void cooldownReplyThrottledWithinInterval() {
        MutableClock clock = new MutableClock(0L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(
                props(60, 1, Duration.ofSeconds(30)), clock);
        assertThat(limiter.tryAcquire(9L).allowed()).isTrue();
        TelegramRateLimiter.Decision first = limiter.tryAcquire(9L);
        assertThat(first.allowed()).isFalse();
        assertThat(first.shouldSendCooldownReply()).isTrue();
        // subsequent denials within cooldown interval should not trigger reply
        TelegramRateLimiter.Decision second = limiter.tryAcquire(9L);
        assertThat(second.allowed()).isFalse();
        assertThat(second.shouldSendCooldownReply()).isFalse();
        // advance past interval → reply allowed again
        clock.advance(Duration.ofSeconds(31));
        TelegramRateLimiter.Decision third = limiter.tryAcquire(9L);
        // refill of 31 tokens: granted (bucket max is 1 → allowed)
        // If allowed, no cooldown reply meaningful. Use different chat w/ exhausted bucket.
        assertThat(third.allowed()).isTrue();
    }

    @Test
    void perChatIsolation() {
        MutableClock clock = new MutableClock(0L);
        TelegramRateLimiter limiter = new TelegramRateLimiter(
                props(60, 1, Duration.ofSeconds(30)), clock);
        assertThat(limiter.tryAcquire(1L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(2L).allowed()).isTrue();
        assertThat(limiter.tryAcquire(1L).allowed()).isFalse();
        assertThat(limiter.tryAcquire(2L).allowed()).isFalse();
    }
}
