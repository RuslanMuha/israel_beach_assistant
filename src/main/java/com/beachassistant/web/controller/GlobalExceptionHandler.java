package com.beachassistant.web.controller;

import com.beachassistant.common.exception.BeachAssistantException;
import com.beachassistant.common.exception.RateLimitedException;
import com.beachassistant.common.exception.Transient;
import com.beachassistant.common.exception.UserFacing;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RateLimitedException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimited(RateLimitedException e) {
        HttpHeaders headers = new HttpHeaders();
        long seconds = e.getRetryAfter() == null ? 30 : Math.max(1, e.getRetryAfter().toSeconds());
        headers.add(HttpHeaders.RETRY_AFTER, Long.toString(seconds));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).headers(headers).body(body(e));
    }

    @ExceptionHandler(BeachAssistantException.class)
    public ResponseEntity<Map<String, Object>> handleDomain(BeachAssistantException e) {
        HttpStatus status = resolveStatus(e);
        if (status.is5xxServerError()) {
            log.error("Domain error [{}]: {}", e.getErrorCode(), e.getMessage(), e);
        } else {
            log.debug("Domain error [{}]: {}", e.getErrorCode(), e.getMessage());
        }
        return ResponseEntity.status(status).body(body(e));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException e) {
        log.debug("No resource: {}", e.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "INTERNAL_ERROR");
        body.put("message", "An unexpected error occurred");
        body.put("timestamp", ZonedDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    private static HttpStatus resolveStatus(BeachAssistantException e) {
        String code = e.getErrorCode();
        if ("BEACH_NOT_FOUND".equals(code) || "CAMERA_NOT_FOUND".equals(code)) {
            return HttpStatus.NOT_FOUND;
        }
        if ("UNSUPPORTED_SOURCE_TYPE".equals(code)) {
            return HttpStatus.BAD_REQUEST;
        }
        if (e instanceof Transient) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (e instanceof UserFacing) {
            return HttpStatus.BAD_REQUEST;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static Map<String, Object> body(BeachAssistantException e) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", e.getErrorCode());
        body.put("message", e.getMessage());
        body.put("timestamp", ZonedDateTime.now().toString());
        return body;
    }
}
