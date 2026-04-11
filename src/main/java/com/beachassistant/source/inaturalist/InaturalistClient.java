package com.beachassistant.source.inaturalist;

import com.beachassistant.config.BeachProvidersProperties;
import com.beachassistant.integration.IntegrationSourceKey;
import com.beachassistant.integration.http.OutboundHttpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * iNaturalist public API — recent citizen-science jellyfish observations near coordinates.
 */
@Slf4j
@Component
public class InaturalistClient {

    private final OutboundHttpService outboundHttpService;
    private final BeachProvidersProperties props;
    private final ObjectMapper objectMapper;

    public InaturalistClient(OutboundHttpService outboundHttpService,
                             BeachProvidersProperties props,
                             ObjectMapper objectMapper) {
        this.outboundHttpService = outboundHttpService;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public ParsedResponse fetchObservations(double latitude, double longitude) {
        String taxonIds = props.getJellyfishTaxonIds().stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        URI uri = UriComponentsBuilder.fromUriString(props.getInaturalistBaseUrl() + "/observations")
                .queryParam("lat", latitude)
                .queryParam("lng", longitude)
                .queryParam("radius", props.getJellyfishSearchRadiusKm())
                .queryParam("taxon_id", taxonIds)
                .queryParam("per_page", 50)
                .queryParam("order", "desc")
                .queryParam("order_by", "observed_on")
                .build()
                .toUri();
        var outcome = outboundHttpService.getJson(IntegrationSourceKey.INATURALIST, uri, "observations");
        if (!outcome.success()) {
            throw new IllegalStateException("iNaturalist fetch failed: " + outcome.errorMessage());
        }
        try {
            JsonNode tree = objectMapper.readTree(outcome.body());
            return new ParsedResponse(tree, outcome.staleFallback(), outcome.shortCircuit());
        } catch (Exception e) {
            throw new IllegalStateException("iNaturalist JSON parse failed: " + e.getMessage(), e);
        }
    }

    public record ParsedResponse(JsonNode json, boolean staleFallback, boolean shortCircuit) {
    }
}
