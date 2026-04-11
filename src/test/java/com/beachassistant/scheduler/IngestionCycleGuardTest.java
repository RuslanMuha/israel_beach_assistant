package com.beachassistant.scheduler;

import com.beachassistant.common.enums.SourceType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionCycleGuardTest {

    @Test
    void tryBegin_secondCallFailsUntilEnd() {
        IngestionCycleGuard guard = new IngestionCycleGuard();
        assertThat(guard.tryBegin(SourceType.SEA_FORECAST)).isTrue();
        assertThat(guard.tryBegin(SourceType.SEA_FORECAST)).isFalse();
        guard.end(SourceType.SEA_FORECAST);
        assertThat(guard.tryBegin(SourceType.SEA_FORECAST)).isTrue();
        guard.end(SourceType.SEA_FORECAST);
    }

    @Test
    void independentLocksPerSource() {
        IngestionCycleGuard guard = new IngestionCycleGuard();
        assertThat(guard.tryBegin(SourceType.SEA_FORECAST)).isTrue();
        assertThat(guard.tryBegin(SourceType.JELLYFISH)).isTrue();
        guard.end(SourceType.SEA_FORECAST);
        guard.end(SourceType.JELLYFISH);
    }
}
