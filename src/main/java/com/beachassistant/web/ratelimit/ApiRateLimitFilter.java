package com.beachassistant.web.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Bucket4j token-bucket filter for {@code /api/v1/beaches/**}. Buckets are keyed by remote IP
 * (or X-Forwarded-For first hop when present); exceeding the limit returns 429 with a
 * {@code Retry-After} header.
 */
@Slf4j
@Component
@Order(50)
@ConditionalOnProperty(prefix = "beach.api.rate-limit", name = "enabled", havingValue = "true")
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final ApiRateLimitProperties props;
    private final Cache<String, Bucket> buckets;

    public ApiRateLimitFilter(ApiRateLimitProperties props) {
        this.props = props;
        this.buckets = Caffeine.newBuilder()
                .expireAfterAccess(props.getBucketIdleTtl())
                .maximumSize(props.getMaxTrackedClients())
                .build();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/v1/beaches")) {
            chain.doFilter(request, response);
            return;
        }
        String clientId = resolveClientId(request);
        Bucket bucket = buckets.get(clientId, this::newBucket);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"RATE_LIMITED\",\"retryAfterSeconds\":"
                + retryAfterSeconds + "}");
    }

    private Bucket newBucket(String key) {
        Refill refill = Refill.greedy(props.getRequestsPerMinute(), Duration.ofMinutes(1));
        Bandwidth limit = Bandwidth.classic(props.getBurst(), refill);
        return Bucket.builder().addLimit(limit).build();
    }

    private static String resolveClientId(HttpServletRequest request) {
        String fwd = request.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) {
            int comma = fwd.indexOf(',');
            return (comma > 0 ? fwd.substring(0, comma) : fwd).trim();
        }
        return request.getRemoteAddr() == null ? "unknown" : request.getRemoteAddr();
    }
}
