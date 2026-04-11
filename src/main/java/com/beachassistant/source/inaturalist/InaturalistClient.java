package com.beachassistant.source.inaturalist;

import com.beachassistant.config.BeachProvidersProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.stream.Collectors;

/**
 * iNaturalist public API — recent citizen-science jellyfish observations near coordinates.
 */
@Slf4j
@Component
public class InaturalistClient {

    private final WebClient webClient;
    private final BeachProvidersProperties props;
    private final ObjectMapper objectMapper;

    public InaturalistClient(WebClient.Builder webClientBuilder,
                             BeachProvidersProperties props,
                             ObjectMapper objectMapper) {
        this.webClient = webClientBuilder.build();
        this.props = props;
        this.objectMapper = objectMapper;
    }

    public JsonNode fetchObservations(double latitude, double longitude) {
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
        Duration timeout = Duration.ofSeconds(props.getHttpTimeoutSeconds());
        String body = webClient.get()
                .uri(uri)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(timeout)
                .block(timeout.plusSeconds(2));
        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("iNaturalist JSON parse failed: " + e.getMessage(), e);
        }
    }
}
