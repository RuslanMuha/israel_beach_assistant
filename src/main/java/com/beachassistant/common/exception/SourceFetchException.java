package com.beachassistant.common.exception;

import com.beachassistant.common.enums.SourceType;

public class SourceFetchException extends RuntimeException {

    private final SourceType sourceType;

    public SourceFetchException(SourceType sourceType, String message, Throwable cause) {
        super("Source fetch failed [" + sourceType + "]: " + message, cause);
        this.sourceType = sourceType;
    }

    public SourceFetchException(SourceType sourceType, String message) {
        super("Source fetch failed [" + sourceType + "]: " + message);
        this.sourceType = sourceType;
    }

    public SourceType getSourceType() {
        return sourceType;
    }
}
