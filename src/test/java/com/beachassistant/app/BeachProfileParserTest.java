package com.beachassistant.app;

import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.persistence.entity.BeachEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BeachProfileParserTest {

    private final BeachProfileParser parser = new BeachProfileParser(new ObjectMapper());

    @Test
    void parse_validJson_returnsProfile() {
        BeachEntity beach = beachWithJson("""
                {
                  "description": "Test beach",
                  "categories": ["family"],
                  "facilities": {"showers": true, "toilets": true, "playground": false,
                    "sportsFacilities": false, "accessible": true, "parking": true},
                  "waterQualityPlaceholder": "N/A"
                }
                """);

        BeachProfile p = parser.parse(beach);
        assertThat(p.description()).isEqualTo("Test beach");
        assertThat(p.categories()).containsExactly("family");
        assertThat(p.facilities().showers()).isTrue();
        assertThat(p.waterQualityPlaceholder()).isEqualTo("N/A");
    }

    @Test
    void parse_null_returnsEmpty() {
        BeachEntity beach = new BeachEntity();
        assertThat(parser.parse(beach).isEmpty()).isTrue();
    }

    @Test
    void parse_invalidJson_returnsEmpty() {
        BeachEntity beach = beachWithJson("{ not json");
        assertThat(parser.parse(beach).isEmpty()).isTrue();
    }

    private static BeachEntity beachWithJson(String json) {
        BeachEntity b = new BeachEntity();
        b.setSlug("test");
        b.setProfileJson(json);
        return b;
    }
}
