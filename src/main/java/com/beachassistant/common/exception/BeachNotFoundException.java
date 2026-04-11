package com.beachassistant.common.exception;

public class BeachNotFoundException extends RuntimeException {

    public BeachNotFoundException(String identifier) {
        super("No beach found for identifier: " + identifier);
    }
}
