package com.beachassistant.domain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Display-oriented beach knowledge loaded from {@code beach.profile_json}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BeachProfile(
        @JsonProperty("description") String description,
        @JsonProperty("categories") List<String> categories,
        @JsonProperty("facilities") BeachFacilities facilities,
        @JsonProperty("accessibilityNotes") String accessibilityNotes,
        @JsonProperty("parkingNotes") String parkingNotes,
        @JsonProperty("notes") String notes,
        @JsonProperty("lifeguardNotes") String lifeguardNotes,
        @JsonProperty("waterQualityPlaceholder") String waterQualityPlaceholder,
        @JsonProperty("jellyfishPlaceholder") String jellyfishPlaceholder
) {
    public static BeachProfile empty() {
        return new BeachProfile(
                null,
                List.of(),
                BeachFacilities.empty(),
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public boolean isEmpty() {
        return (description == null || description.isBlank())
                && categories.isEmpty()
                && facilities.equals(BeachFacilities.empty())
                && (accessibilityNotes == null || accessibilityNotes.isBlank())
                && (parkingNotes == null || parkingNotes.isBlank())
                && (notes == null || notes.isBlank())
                && (lifeguardNotes == null || lifeguardNotes.isBlank())
                && (waterQualityPlaceholder == null || waterQualityPlaceholder.isBlank())
                && (jellyfishPlaceholder == null || jellyfishPlaceholder.isBlank());
    }

    public BeachProfile {
        categories = categories != null ? List.copyOf(categories) : List.of();
        facilities = facilities != null ? facilities : BeachFacilities.empty();
    }
}
