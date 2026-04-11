package com.beachassistant.app;

import com.beachassistant.domain.model.BeachProfile;
import com.beachassistant.persistence.entity.BeachEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BeachProfileParser {

    private final ObjectMapper objectMapper;

    public BeachProfileParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BeachProfile parse(BeachEntity beach) {
        String raw = beach.getProfileJson();
        if (raw == null || raw.isBlank()) {
            return BeachProfile.empty();
        }
        try {
            return objectMapper.readValue(raw, BeachProfile.class);
        } catch (Exception e) {
            log.warn("Invalid profile_json for beach slug={}: {}", beach.getSlug(), e.getMessage());
            return BeachProfile.empty();
        }
    }
}
