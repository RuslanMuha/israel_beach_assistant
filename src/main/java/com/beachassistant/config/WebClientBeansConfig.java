package com.beachassistant.config;

import com.beachassistant.integration.http.BeachIntegrationProperties;
import com.beachassistant.integration.http.OutboundHttpService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(BeachIntegrationProperties.class)
public class WebClientBeansConfig {

    @Bean
    public WebClient outboundWebClient(BeachIntegrationProperties integration,
                                       BeachProvidersProperties beachProvidersProperties) {
        int maxBytes = beachProvidersProperties.getHttpMaxResponseBufferBytes();
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(OutboundHttpService.createUnderlyingHttpClient(integration)))
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxBytes))
                .defaultHeader(HttpHeaders.USER_AGENT, "BeachAssistant/0.1 (https://open-meteo.com; https://inaturalist.org)")
                .build();
    }
}
