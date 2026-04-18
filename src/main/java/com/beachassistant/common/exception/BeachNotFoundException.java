package com.beachassistant.common.exception;

public class BeachNotFoundException extends BeachAssistantException implements UserFacing {

    public BeachNotFoundException(String identifier) {
        super("BEACH_NOT_FOUND", "No beach found for identifier: " + identifier);
    }
}
