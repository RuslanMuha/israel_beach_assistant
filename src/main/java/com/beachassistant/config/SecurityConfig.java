package com.beachassistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AdminApiProperties.class)
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AdminApiProperties adminApiProperties) throws Exception {
        http
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll();
                    auth.requestMatchers(HttpMethod.GET, "/api/v1/beaches/**").permitAll();
                    auth.requestMatchers("/api/telegram/**").permitAll();
                    if (adminApiProperties.isApiTokenConfigured()) {
                        auth.requestMatchers("/api/v1/admin/**").authenticated();
                    } else {
                        auth.requestMatchers("/api/v1/admin/**").permitAll();
                    }
                    auth.anyRequest().permitAll();
                })
                .addFilterBefore(new AdminApiTokenAuthFilter(adminApiProperties), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
