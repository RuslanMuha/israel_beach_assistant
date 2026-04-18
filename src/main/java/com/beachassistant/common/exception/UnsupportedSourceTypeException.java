package com.beachassistant.common.exception;

public class UnsupportedSourceTypeException extends BeachAssistantException implements UserFacing {

    public UnsupportedSourceTypeException(String sourceType) {
        super("UNSUPPORTED_SOURCE_TYPE", "Unsupported source type: " + sourceType);
    }
}
