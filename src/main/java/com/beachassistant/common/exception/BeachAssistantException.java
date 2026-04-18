package com.beachassistant.common.exception;

/**
 * Root for all domain exceptions thrown by Beach Assistant. Subtypes implement marker interfaces
 * ({@link UserFacing}, {@link Transient}) so handlers can reason about safety of retries and
 * disclosure to end users without switching on concrete types.
 */
public abstract class BeachAssistantException extends RuntimeException {

    private final String errorCode;

    protected BeachAssistantException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    protected BeachAssistantException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
