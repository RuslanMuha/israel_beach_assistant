package com.beachassistant.domain.flag;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SwimFlagKnowledgeTest {

    @Test
    void compactLegend_containsAllFlags() {
        String legend = SwimFlagKnowledge.compactLegendRu();
        assertThat(legend).contains("черный").contains("красный").contains("жёлтый").contains("зелёный");
    }

    @Test
    void eachFlag_hasDisplayParts() {
        for (SwimFlagKnowledge f : SwimFlagKnowledge.values()) {
            assertThat(f.colorNameRu()).isNotBlank();
            assertThat(f.displayLabelRu()).isNotBlank();
            assertThat(f.swimStanceRu()).isNotBlank();
            assertThat(f.shortExplanationRu()).isNotBlank();
        }
    }
}
