package com.beachassistant.source.jellyfish;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class JellyfishObservedOnParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesIsoLocalDate() throws Exception {
        var node = mapper.readTree("{\"observed_on\":\"2024-06-01\"}");
        assertThat(JellyfishObservedOnParser.parseObservedLocalDate(node))
                .isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void parsesOffsetDateTimeToLocalDate() throws Exception {
        var node = mapper.readTree("{\"observed_on\":\"2024-06-01T12:00:00+03:00\"}");
        assertThat(JellyfishObservedOnParser.parseObservedLocalDate(node))
                .isEqualTo(LocalDate.of(2024, 6, 1));
    }

    @Test
    void fallsBackToObservedOnDetailsDate() throws Exception {
        var node = mapper.readTree("{\"observed_on\":null,\"observed_on_details\":{\"date\":\"2023-12-25\"}}");
        assertThat(JellyfishObservedOnParser.parseObservedLocalDate(node))
                .isEqualTo(LocalDate.of(2023, 12, 25));
    }

    @Test
    void returnsNullWhenUnparseable() throws Exception {
        var node = mapper.readTree("{\"observed_on\":\"not-a-date\"}");
        assertThat(JellyfishObservedOnParser.parseObservedLocalDate(node)).isNull();
    }
}
