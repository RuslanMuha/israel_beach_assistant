package com.beachassistant.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Sets an authenticated principal when {@link AdminApiProperties#isApiTokenConfigured()} and the request
 * carries the matching {@code X-Admin-Token} header.
 */
public class AdminApiTokenAuthFilter extends OncePerRequestFilter {

    static final String ADMIN_TOKEN_HEADER = "X-Admin-Token";

    private final AdminApiProperties adminApiProperties;

    public AdminApiTokenAuthFilter(AdminApiProperties adminApiProperties) {
        this.adminApiProperties = adminApiProperties;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        if (!adminApiProperties.isApiTokenConfigured()) {
            return true;
        }
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith("/api/v1/admin");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        String presented = request.getHeader(ADMIN_TOKEN_HEADER);
        if (presented != null && constantTimeEquals(presented, adminApiProperties.getApiToken())) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "admin",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            filterChain.doFilter(request, response);
            SecurityContextHolder.clearContext();
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"admin\"");
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"UNAUTHORIZED\",\"message\":\"Missing or invalid X-Admin-Token\"}");
    }

    /**
     * Timing-safe equality: avoids leaking token length or matching-prefix information through
     * {@link String#equals} early-exit behaviour.
     */
    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] ba = a.getBytes(StandardCharsets.UTF_8);
        byte[] bb = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(ba, bb);
    }
}
