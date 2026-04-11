package com.beachassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder(BeachProvidersProperties beachProvidersProperties) {
        int maxBytes = beachProvidersProperties.getHttpMaxResponseBufferBytes();
        return WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxBytes))
                .defaultHeader(HttpHeaders.USER_AGENT, "BeachAssistant/0.1 (https://open-meteo.com; https://inaturalist.org)");
    }
}
