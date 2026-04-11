package com.beachassistant.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Protects admin HTTP endpoints when {@link #apiToken} is set (via env {@code BEACH_ADMIN_API_TOKEN}).
 * When empty, admin routes remain open (local/dev); set the token in production.
 */
@Validated
@Getter
@Setter
@ConfigurationProperties(prefix = "beach.admin")
public class AdminApiProperties {

    /**
     * When non-blank, {@code POST /api/v1/admin/**} requires header {@code X-Admin-Token} with this value.
     */
    private String apiToken = "";

    public boolean isApiTokenConfigured() {
        return apiToken != null && !apiToken.isBlank();
    }
}
