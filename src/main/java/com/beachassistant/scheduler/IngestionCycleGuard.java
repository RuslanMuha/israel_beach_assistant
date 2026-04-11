package com.beachassistant.scheduler;

import com.beachassistant.common.enums.SourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Prevents overlapping runs of the same ingestion flow when configured (single-flight per {@link SourceType}).
 * Uses compare-and-set (not reentrant): a second attempt from the same thread also fails while a cycle is active.
 */
@Component
public class IngestionCycleGuard {

    private final Map<SourceType, AtomicBoolean> running = new EnumMap<>(SourceType.class);

    public boolean tryBegin(SourceType sourceType) {
        return running.computeIfAbsent(sourceType, st -> new AtomicBoolean(false)).compareAndSet(false, true);
    }

    public void end(SourceType sourceType) {
        AtomicBoolean flag = running.get(sourceType);
        if (flag != null) {
            flag.set(false);
        }
    }
}
