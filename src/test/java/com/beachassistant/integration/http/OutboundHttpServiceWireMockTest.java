package com.beachassistant.integration.http;

import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.integration.IntegrationSourceKey;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

class OutboundHttpServiceWireMockTest {

    @RegisterExtension
    static final WireMockExtension WIRE_MOCK = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @Test
    void getJson_retries429ThenSucceeds() {
        BeachIntegrationProperties integration = fastRetryIntegration();
        WebClient webClient = webClientForIntegration(integration);

        WIRE_MOCK.stubFor(get(urlPathEqualTo("/marine"))
                .inScenario("retry429")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withHeader("Retry-After", "0"))
                .willSetStateTo("ok"));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/marine"))
                .inScenario("retry429")
                .whenScenarioStateIs("ok")
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json")
                        .withBody("{\"hourly\":{\"time\":[\"2024-01-01T00:00\"]}}")));

        OutboundHttpService svc = new OutboundHttpService(webClient, integration,
                new BeachMetrics(new SimpleMeterRegistry()), Clock.systemUTC(), testRegistry());

        URI uri = URI.create(WIRE_MOCK.getRuntimeInfo().getHttpBaseUrl().replaceAll("/$", "") + "/marine");
        HttpJsonOutcome out = svc.getJson(IntegrationSourceKey.OPEN_METEO, uri, "marine");

        assertThat(out.success()).isTrue();
        assertThat(out.body()).contains("hourly");
        assertThat(out.attempts()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void getJson_doesNotRetry403() {
        BeachIntegrationProperties integration = fastRetryIntegration();
        WebClient webClient = webClientForIntegration(integration);

        WIRE_MOCK.stubFor(get(urlPathEqualTo("/forbidden"))
                .willReturn(aResponse().withStatus(403).withBody("no")));

        OutboundHttpService svc = new OutboundHttpService(webClient, integration,
                new BeachMetrics(new SimpleMeterRegistry()), Clock.systemUTC(), testRegistry());

        URI uri = URI.create(WIRE_MOCK.getRuntimeInfo().getHttpBaseUrl().replaceAll("/$", "") + "/forbidden");
        HttpJsonOutcome out = svc.getJson(IntegrationSourceKey.OPEN_METEO, uri, "forbidden");

        assertThat(out.success()).isFalse();
        assertThat(out.attempts()).isEqualTo(1);
    }

    @Test
    void getJson_servesStaleBodyWhenLiveFails() {
        BeachIntegrationProperties integration = fastRetryIntegration();
        integration.getOpenMeteo().setShortCircuitTtl(Duration.ZERO);
        integration.getOpenMeteo().setStaleFallbackMaxAge(Duration.ofHours(1));
        WebClient webClient = webClientForIntegration(integration);

        WIRE_MOCK.stubFor(get(urlPathEqualTo("/stale"))
                .inScenario("stale")
                .whenScenarioStateIs(com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED)
                .willReturn(aResponse().withStatus(200).withBody("{\"v\":1}"))
                .willSetStateTo("bad"));
        WIRE_MOCK.stubFor(get(urlPathEqualTo("/stale"))
                .inScenario("stale")
                .whenScenarioStateIs("bad")
                .willReturn(aResponse().withStatus(503)));

        OutboundHttpService svc = new OutboundHttpService(webClient, integration,
                new BeachMetrics(new SimpleMeterRegistry()), Clock.systemUTC(), testRegistry());

        String base = WIRE_MOCK.getRuntimeInfo().getHttpBaseUrl().replaceAll("/$", "");
        URI uri = URI.create(base + "/stale");
        assertThat(svc.getJson(IntegrationSourceKey.OPEN_METEO, uri, "stale").success()).isTrue();

        HttpJsonOutcome second = svc.getJson(IntegrationSourceKey.OPEN_METEO, uri, "stale");
        assertThat(second.success()).isTrue();
        assertThat(second.staleFallback()).isTrue();
        assertThat(second.body()).contains("\"v\":1");
    }

    private static BeachIntegrationProperties fastRetryIntegration() {
        BeachIntegrationProperties integration = new BeachIntegrationProperties();
        integration.getOpenMeteo().setConnectTimeout(Duration.ofSeconds(2));
        integration.getOpenMeteo().setReadTimeout(Duration.ofSeconds(2));
        integration.getOpenMeteo().setMaxRetries(4);
        integration.getOpenMeteo().setInitialBackoff(Duration.ofMillis(1));
        integration.getOpenMeteo().setMaxBackoff(Duration.ofMillis(20));
        integration.getOpenMeteo().setBackoffMultiplier(2.0);
        integration.getOpenMeteo().setShortCircuitTtl(Duration.ZERO);
        integration.getOpenMeteo().setStaleFallbackMaxAge(Duration.ofHours(1));
        integration.getOpenMeteo().setPacingMillisBetweenRequests(0L);
        integration.setHttpResponseCacheTtl(Duration.ofHours(6));
        integration.getInaturalist().setConnectTimeout(Duration.ofSeconds(2));
        integration.getInaturalist().setReadTimeout(Duration.ofSeconds(2));
        integration.getCamera().setConnectTimeout(Duration.ofSeconds(2));
        integration.getCamera().setReadTimeout(Duration.ofSeconds(2));
        return integration;
    }

    /**
     * Permissive registry so a few test calls never open the circuit; names must match production beans.
     */
    private static CircuitBreakerRegistry testRegistry() {
        CircuitBreakerConfig cfg = CircuitBreakerConfig.custom()
                .slidingWindowSize(200)
                .minimumNumberOfCalls(500)
                .failureRateThreshold(100)
                .waitDurationInOpenState(Duration.ofMillis(100))
                .recordExceptions(OutboundCallsFailedException.class)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(cfg);
        registry.circuitBreaker(OutboundHttpService.circuitBreakerName(IntegrationSourceKey.OPEN_METEO));
        registry.circuitBreaker(OutboundHttpService.circuitBreakerName(IntegrationSourceKey.INATURALIST));
        registry.circuitBreaker(OutboundHttpService.circuitBreakerName(IntegrationSourceKey.CAMERA));
        return registry;
    }

    private static WebClient webClientForIntegration(BeachIntegrationProperties integration) {
        HttpClient httpClient = OutboundHttpService.createUnderlyingHttpClient(integration);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(c -> c.defaultCodecs().maxInMemorySize(2_097_152))
                .build();
    }
}
