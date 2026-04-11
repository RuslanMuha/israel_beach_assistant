package com.beachassistant.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Static facility flags for a beach (from {@code beach.profile_json}).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BeachFacilities(
        @JsonProperty("showers") boolean showers,
        @JsonProperty("toilets") boolean toilets,
        @JsonProperty("playground") boolean playground,
        @JsonProperty("sportsFacilities") boolean sportsFacilities,
        @JsonProperty("accessible") boolean accessible,
        @JsonProperty("parking") boolean parking
) {
    public static BeachFacilities empty() {
        return new BeachFacilities(false, false, false, false, false, false);
    }
}
