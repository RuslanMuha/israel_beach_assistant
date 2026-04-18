package com.beachassistant.telegram.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-chat single-flight gate. Prevents a second concurrent handler for the same {@code chatId}
 * while one is already running, avoiding duplicate outbound messages when users double-tap
 * buttons or rapidly re-send commands.
 *
 * <p>Backed by a Caffeine cache so abandoned entries (e.g. crashed handlers that somehow never
 * released) evict automatically instead of leaking forever.</p>
 */
@Component
public class ChatSingleFlightGuard {

    private final Cache<Long, AtomicBoolean> gates = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(10_000)
            .build();

    /**
     * Attempts to acquire the gate for this chat. Returns {@code true} when the caller holds the
     * gate and should proceed; {@code false} when another handler is already in flight.
     */
    public boolean tryBegin(long chatId) {
        AtomicBoolean gate = gates.get(chatId, id -> new AtomicBoolean(false));
        //noinspection ConstantConditions
        return gate.compareAndSet(false, true);
    }

    public void end(long chatId) {
        AtomicBoolean gate = gates.getIfPresent(chatId);
        if (gate != null) {
            gate.set(false);
        }
    }
}
