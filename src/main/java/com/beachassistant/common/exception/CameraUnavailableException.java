package com.beachassistant.common.exception;

public class CameraUnavailableException extends BeachAssistantException implements UserFacing {

    public CameraUnavailableException(String beachSlug) {
        super("CAMERA_NOT_FOUND", "No active camera for beach: " + beachSlug);
    }
}
