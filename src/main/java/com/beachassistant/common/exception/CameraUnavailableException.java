package com.beachassistant.common.exception;

public class CameraUnavailableException extends RuntimeException {

    public CameraUnavailableException(String beachSlug) {
        super("No active camera for beach: " + beachSlug);
    }
}
