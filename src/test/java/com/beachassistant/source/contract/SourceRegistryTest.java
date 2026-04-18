package com.beachassistant.source.contract;

import com.beachassistant.common.enums.SourceType;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SourceRegistryTest {

    @Test
    void defaultDescriptorUsesSourceType() {
        FakeAdapter legacy = new FakeAdapter(SourceType.SEA_FORECAST);
        SourceRegistry reg = new SourceRegistry(List.of(legacy), new SourceProviderProperties());

        assertThat(reg.adapters()).hasSize(1);
        assertThat(reg.findById("sea-forecast")).isPresent();
        assertThat(reg.adapters().get(0).descriptor().defaultCadence()).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void disabledAdapterIsFilteredOutByProperties() {
        FakeAdapter a = new FakeAdapter(SourceType.SEA_FORECAST);
        FakeAdapter b = new FakeAdapter(SourceType.JELLYFISH);
        SourceProviderProperties props = new SourceProviderProperties();
        SourceProviderProperties.Entry disabled = new SourceProviderProperties.Entry();
        disabled.setEnabled(false);
        props.getAdapters().put("jellyfish", disabled);

        SourceRegistry reg = new SourceRegistry(List.of(a, b), props);

        assertThat(reg.enabledAdapters())
                .extracting(ad -> ad.descriptor().id())
                .containsExactly("sea-forecast");
    }

    private static final class FakeAdapter implements SourceAdapter<Void> {
        private final SourceType type;

        private FakeAdapter(SourceType type) {
            this.type = type;
        }

        @Override
        public SourceType sourceType() {
            return type;
        }

        @Override
        public FetchResult<Void> fetch(SourceRequest request) {
            return null;
        }
    }
}
