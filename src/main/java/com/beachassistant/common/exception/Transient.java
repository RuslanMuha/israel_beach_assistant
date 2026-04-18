package com.beachassistant.common.exception;

/**
 * Marker: failure is expected to recover on retry (network blip, upstream throttle, transient 5xx).
 */
public interface Transient {
}
