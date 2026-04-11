package com.beachassistant.integration.http;

/**
 * Live outbound fetch exhausted retries without a successful response. Used so Resilience4j can record failures;
 * not an unexpected bug (typically logged at WARN upstream).
 */
public final class OutboundCallsFailedException extends RuntimeException {

    private final int attempts;
    private final int lastHttpStatus;

    public OutboundCallsFailedException(String message, int attempts, int lastHttpStatus) {
        super(message);
        this.attempts = attempts;
        this.lastHttpStatus = lastHttpStatus;
    }

    public int getAttempts() {
        return attempts;
    }

    public int getLastHttpStatus() {
        return lastHttpStatus;
    }
}
