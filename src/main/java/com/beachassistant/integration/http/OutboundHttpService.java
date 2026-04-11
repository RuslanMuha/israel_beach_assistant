package com.beachassistant.integration.http;

import com.beachassistant.common.util.BeachMetrics;
import com.beachassistant.integration.IntegrationSourceKey;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.netty.http.client.HttpClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Shared outbound GET/HEAD with pacing, bounded retries (429 + transient 5xx + timeouts), short-circuit cache,
 * stale fallback, and per-source Resilience4j circuit breakers. Retries are safe for idempotent reads only.
 */
@Slf4j
@Component
public class OutboundHttpService {

    private static final int MAX_CACHE_ENTRIES = 10_000;

    private final WebClient webClient;
    private final BeachIntegrationProperties integration;
    private final BeachMetrics metrics;
    private final Clock clock;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final Cache<String, CachedPayload> jsonCache;
    private final ConcurrentHashMap<IntegrationSourceKey, Object> pacingLocks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<IntegrationSourceKey, AtomicLong> lastRequestEndNanos = new ConcurrentHashMap<>();

    public OutboundHttpService(WebClient outboundWebClient,
                               BeachIntegrationProperties integration,
                               BeachMetrics metrics,
                               Clock clock,
                               CircuitBreakerRegistry circuitBreakerRegistry) {
        this.webClient = outboundWebClient;
        this.integration = integration;
        this.metrics = metrics;
        this.clock = clock;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        long cacheTtlMs = Math.max(1L, integration.getHttpResponseCacheTtl().toMillis());
        this.jsonCache = Caffeine.newBuilder()
                .maximumSize(MAX_CACHE_ENTRIES)
                .expireAfterWrite(cacheTtlMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * Resilient JSON GET: may short-circuit from cache, retry on retryable failures, then fall back to stale cache.
     */
    public HttpJsonOutcome getJson(IntegrationSourceKey sourceKey, URI uri, String operation) {
        HttpSourceProperties p = integration.forSource(sourceKey);
        if (!p.isEnabled()) {
            return new HttpJsonOutcome(null, false, false, 0, 0, "integration disabled for source=" + sourceKey);
        }
        String cacheKey = cacheKey(sourceKey, uri);
        CachedPayload cached = jsonCache.getIfPresent(cacheKey);
        Instant now = clock.instant();
        if (p.getShortCircuitTtl().toMillis() > 0 && cached != null
                && Duration.between(cached.storedAt(), now).compareTo(p.getShortCircuitTtl()) <= 0) {
            metrics.recordIntegrationCacheHit(sourceKey, "short_circuit");
            log.debug("integration_http cache short_circuit source={} op={} uri={}", sourceKey, operation, uri);
            return new HttpJsonOutcome(cached.body(), true, false, 0, 200, null);
        }

        try {
            if (p.isCircuitBreakerEnabled()) {
                return circuitBreakerRegistry.circuitBreaker(circuitBreakerName(sourceKey))
                        .executeSupplier(() -> liveFetchJson(sourceKey, uri, operation, cacheKey, p));
            }
            return liveFetchJson(sourceKey, uri, operation, cacheKey, p);
        } catch (CallNotPermittedException e) {
            metrics.recordCircuitBreakerNotPermitted(sourceKey);
            log.warn("integration_http circuit_open source={} op={} uri={}", sourceKey, operation, uri);
            return staleJsonFallbackAfterFailure(sourceKey, uri, operation, p, cacheKey, 0, 0);
        } catch (OutboundCallsFailedException e) {
            return staleJsonFallbackAfterFailure(sourceKey, uri, operation, p, cacheKey, e.getAttempts(), e.getLastHttpStatus());
        }
    }

    /**
     * Live GET with retries; throws when no successful body so the circuit breaker records failures.
     */
    private HttpJsonOutcome liveFetchJson(IntegrationSourceKey sourceKey, URI uri, String operation,
                                          String cacheKey, HttpSourceProperties p) {
        Duration budget = p.getReadTimeout().plus(Duration.ofSeconds(2));
        int attempt = 0;
        int lastStatus = 0;
        while (true) {
            attempt++;
            applyPacing(sourceKey, p);
            long t0 = System.nanoTime();
            try {
                String body = webClient.get()
                        .uri(uri)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(p.getReadTimeout())
                        .block(budget);
                lastStatus = 200;
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                metrics.recordIntegrationHttp(sourceKey, operation, "success");
                metrics.recordIntegrationLatency(sourceKey, ms);
                jsonCache.put(cacheKey, new CachedPayload(body, clock.instant()));
                markPacing(sourceKey);
                return new HttpJsonOutcome(body, false, false, attempt, lastStatus, null);
            } catch (WebClientResponseException ex) {
                lastStatus = ex.getStatusCode().value();
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                metrics.recordIntegrationHttp(sourceKey, operation, "http_error");
                metrics.recordIntegrationLatency(sourceKey, ms);
                if (isRetryableStatus(lastStatus) && attempt <= p.getMaxRetries()) {
                    metrics.recordIntegrationRetry(sourceKey);
                    if (lastStatus == 429) {
                        metrics.recordIntegrationRateLimit(sourceKey);
                    }
                    markPacing(sourceKey);
                    log.warn("integration_http retryable status source={} op={} uri={} status={} attempt={}/{}",
                            sourceKey, operation, uri, lastStatus, attempt, p.getMaxRetries() + 1);
                    if (!sleepBackoff(sourceKey, p, attempt, ex)) {
                        break;
                    }
                    continue;
                }
                log.warn("integration_http non-retryable client error source={} op={} uri={} status={} body_snip={}",
                        sourceKey, operation, uri, lastStatus, abbreviate(ex.getResponseBodyAsString()));
                markPacing(sourceKey);
                break;
            } catch (WebClientRequestException | IllegalStateException ex) {
                lastStatus = 0;
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                metrics.recordIntegrationHttp(sourceKey, operation, "transport_error");
                metrics.recordIntegrationLatency(sourceKey, ms);
                markPacing(sourceKey);
                if (attempt <= p.getMaxRetries() && isRetryableTransport(ex)) {
                    metrics.recordIntegrationRetry(sourceKey);
                    log.warn("integration_http transport error source={} op={} uri={} attempt={}/{} msg={}",
                            sourceKey, operation, uri, attempt, p.getMaxRetries() + 1, ex.getMessage());
                    if (!sleepBackoff(sourceKey, p, attempt, ex)) {
                        break;
                    }
                    continue;
                }
                log.warn("integration_http transport exhausted source={} op={} uri={} msg={}",
                        sourceKey, operation, uri, ex.getMessage());
                break;
            } catch (RuntimeException ex) {
                lastStatus = 0;
                metrics.recordIntegrationHttp(sourceKey, operation, "unexpected");
                if (attempt <= p.getMaxRetries() && isRetryableTransport(ex)) {
                    markPacing(sourceKey);
                    metrics.recordIntegrationRetry(sourceKey);
                    log.warn("integration_http transport error source={} op={} uri={} attempt={}/{} msg={}",
                            sourceKey, operation, uri, attempt, p.getMaxRetries() + 1, ex.getMessage());
                    if (!sleepBackoff(sourceKey, p, attempt, ex)) {
                        break;
                    }
                    continue;
                }
                log.error("integration_http unexpected source={} op={} uri={}", sourceKey, operation, uri, ex);
                markPacing(sourceKey);
                break;
            }
        }
        throw new OutboundCallsFailedException("fetch failed for " + operation, attempt, lastStatus);
    }

    private HttpJsonOutcome staleJsonFallbackAfterFailure(IntegrationSourceKey sourceKey, URI uri, String operation,
                                                          HttpSourceProperties p, String cacheKey,
                                                          int attempts, int lastStatus) {
        CachedPayload cached = jsonCache.getIfPresent(cacheKey);
        if (p.getStaleFallbackMaxAge().toMillis() > 0 && cached != null
                && Duration.between(cached.storedAt(), clock.instant()).compareTo(p.getStaleFallbackMaxAge()) <= 0) {
            metrics.recordIntegrationCacheHit(sourceKey, "stale_fallback");
            log.warn("integration_http stale_fallback source={} op={} uri={} lastStatus={}", sourceKey, operation, uri, lastStatus);
            return new HttpJsonOutcome(cached.body(), false, true, attempts, lastStatus, null);
        }
        return new HttpJsonOutcome(null, false, false, attempts, lastStatus, "fetch failed for " + operation);
    }

    /**
     * HEAD request with bounded retries for reachability checks (no body cache).
     */
    public HeadExchangeOutcome head(IntegrationSourceKey sourceKey, URI uri, String operation) {
        HttpSourceProperties p = integration.forSource(sourceKey);
        if (!p.isEnabled()) {
            return new HeadExchangeOutcome(0, false, 0, "integration disabled for source=" + sourceKey);
        }
        try {
            if (p.isCircuitBreakerEnabled()) {
                return circuitBreakerRegistry.circuitBreaker(circuitBreakerName(sourceKey))
                        .executeSupplier(() -> headLiveFetch(sourceKey, uri, operation, p));
            }
            return headLiveFetch(sourceKey, uri, operation, p);
        } catch (CallNotPermittedException e) {
            metrics.recordCircuitBreakerNotPermitted(sourceKey);
            log.warn("integration_http head circuit_open source={} op={} uri={}", sourceKey, operation, uri);
            return new HeadExchangeOutcome(0, false, 0, "circuit open");
        } catch (OutboundCallsFailedException e) {
            return new HeadExchangeOutcome(e.getLastHttpStatus(), false, e.getAttempts(), e.getMessage());
        }
    }

    private HeadExchangeOutcome headLiveFetch(IntegrationSourceKey sourceKey, URI uri, String operation, HttpSourceProperties p) {
        Duration budget = p.getReadTimeout().plus(Duration.ofSeconds(2));
        int attempt = 0;
        int lastStatus = 0;
        while (true) {
            attempt++;
            applyPacing(sourceKey, p);
            long t0 = System.nanoTime();
            try {
                var response = webClient.head()
                        .uri(uri)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(p.getReadTimeout())
                        .block(budget);
                lastStatus = response != null && response.getStatusCode() != null
                        ? response.getStatusCode().value()
                        : 200;
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                metrics.recordIntegrationHttp(sourceKey, operation, "success");
                metrics.recordIntegrationLatency(sourceKey, ms);
                markPacing(sourceKey);
                boolean ok = lastStatus >= 200 && lastStatus < 400;
                return new HeadExchangeOutcome(lastStatus, ok, attempt, ok ? null : "non-success status");
            } catch (WebClientResponseException ex) {
                lastStatus = ex.getStatusCode().value();
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                metrics.recordIntegrationHttp(sourceKey, operation, "http_error");
                metrics.recordIntegrationLatency(sourceKey, ms);
                if (isRetryableStatus(lastStatus) && attempt <= p.getMaxRetries()) {
                    metrics.recordIntegrationRetry(sourceKey);
                    if (lastStatus == 429) {
                        metrics.recordIntegrationRateLimit(sourceKey);
                    }
                    if (!sleepBackoff(sourceKey, p, attempt, ex)) {
                        break;
                    }
                    continue;
                }
                markPacing(sourceKey);
                return new HeadExchangeOutcome(lastStatus, false, attempt, ex.getMessage());
            } catch (WebClientRequestException | IllegalStateException ex) {
                lastStatus = 0;
                long ms = Duration.ofNanos(System.nanoTime() - t0).toMillis();
                metrics.recordIntegrationHttp(sourceKey, operation, "transport_error");
                metrics.recordIntegrationLatency(sourceKey, ms);
                if (attempt <= p.getMaxRetries()) {
                    metrics.recordIntegrationRetry(sourceKey);
                    if (!sleepBackoff(sourceKey, p, attempt, ex)) {
                        break;
                    }
                    continue;
                }
                markPacing(sourceKey);
                return new HeadExchangeOutcome(0, false, attempt, ex.getMessage());
            } catch (RuntimeException ex) {
                log.error("integration_http head unexpected source={} op={} uri={}", sourceKey, operation, uri, ex);
                markPacing(sourceKey);
                return new HeadExchangeOutcome(0, false, attempt, ex.getMessage());
            }
        }
        markPacing(sourceKey);
        throw new OutboundCallsFailedException("head failed for " + operation, attempt, lastStatus);
    }

    static String circuitBreakerName(IntegrationSourceKey sourceKey) {
        return switch (sourceKey) {
            case OPEN_METEO -> "integration-open-meteo";
            case INATURALIST -> "integration-inaturalist";
            case CAMERA -> "integration-camera";
        };
    }

    /**
     * Builds a shared Reactor Netty {@link HttpClient} using the maximum connect/read across configured sources.
     */
    public static HttpClient createUnderlyingHttpClient(BeachIntegrationProperties integration) {
        HttpSourceProperties om = integration.getOpenMeteo();
        HttpSourceProperties ina = integration.getInaturalist();
        HttpSourceProperties cam = integration.getCamera();
        long connectMs = Math.max(om.getConnectTimeout().toMillis(),
                Math.max(ina.getConnectTimeout().toMillis(), cam.getConnectTimeout().toMillis()));
        long responseMs = Math.max(om.getReadTimeout().toMillis(),
                Math.max(ina.getReadTimeout().toMillis(), cam.getReadTimeout().toMillis()));
        int connect = (int) Math.min(connectMs, Integer.MAX_VALUE);
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connect)
                .responseTimeout(Duration.ofMillis(responseMs));
    }

    private static String cacheKey(IntegrationSourceKey sourceKey, URI uri) {
        return sourceKey.name() + "|" + uri.normalize();
    }

    private static boolean isRetryableStatus(int status) {
        return status == 429 || status == 408 || (status >= 500 && status <= 599);
    }

    /**
     * Timeouts and I/O errors are treated as retryable; business 4xx from {@link WebClientResponseException} are not.
     */
    private static boolean isRetryableTransport(Throwable ex) {
        if (ex instanceof WebClientRequestException) {
            return true;
        }
        if (ex instanceof IllegalStateException) {
            return true;
        }
        Throwable c = ex;
        while (c != null) {
            if (c instanceof TimeoutException) {
                return true;
            }
            if (c instanceof java.io.IOException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    private void applyPacing(IntegrationSourceKey sourceKey, HttpSourceProperties p) {
        long spacingMs = p.getPacingMillisBetweenRequests();
        if (spacingMs <= 0) {
            return;
        }
        Object lock = pacingLocks.computeIfAbsent(sourceKey, k -> new Object());
        synchronized (lock) {
            AtomicLong last = lastRequestEndNanos.computeIfAbsent(sourceKey, k -> new AtomicLong(0L));
            long nano = System.nanoTime();
            long waitNs = last.get() + Duration.ofMillis(spacingMs).toNanos() - nano;
            if (waitNs > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(waitNs));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void markPacing(IntegrationSourceKey sourceKey) {
        lastRequestEndNanos.computeIfAbsent(sourceKey, k -> new AtomicLong(0L)).set(System.nanoTime());
    }

    /**
     * Backoff with jitter; honors Retry-After for 429 when present on {@link WebClientResponseException}.
     * Returns false if interrupted.
     */
    private boolean sleepBackoff(IntegrationSourceKey sourceKey, HttpSourceProperties p, int attemptNumber, Exception ex) {
        long base = p.getInitialBackoff().toMillis();
        double mult = p.getBackoffMultiplier();
        long exp = (long) (base * Math.pow(mult, Math.max(0, attemptNumber - 1)));
        long capped = Math.min(exp, p.getMaxBackoff().toMillis());
        long jitter = ThreadLocalRandom.current().nextLong(0, Math.max(1L, capped / 5));
        long sleepMs = capped + jitter;
        if (ex instanceof WebClientResponseException wcre && wcre.getStatusCode().value() == 429) {
            var ra = parseRetryAfterSeconds(wcre);
            if (ra.isPresent()) {
                sleepMs = Math.max(sleepMs, ra.getAsLong() * 1000L);
            }
            sleepMs = Math.min(sleepMs, p.getMaxBackoff().toMillis());
        }
        try {
            Thread.sleep(sleepMs);
            return !Thread.currentThread().isInterrupted();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("integration_http backoff interrupted source={}", sourceKey);
            return false;
        }
    }

    private static java.util.OptionalLong parseRetryAfterSeconds(WebClientResponseException ex) {
        String ra = ex.getHeaders().getFirst("Retry-After");
        if (ra == null || ra.isBlank()) {
            return java.util.OptionalLong.empty();
        }
        try {
            return java.util.OptionalLong.of(Long.parseLong(ra.trim()));
        } catch (NumberFormatException ignored) {
            return java.util.OptionalLong.empty();
        }
    }

    private static String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace('\n', ' ');
        return t.length() > 160 ? t.substring(0, 160) + "…" : t;
    }

    private record CachedPayload(String body, Instant storedAt) {
    }
}
