package com.beachassistant.web.controller;

import com.beachassistant.common.exception.BeachNotFoundException;
import com.beachassistant.common.exception.CameraUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.ZonedDateTime;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BeachNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleBeachNotFound(BeachNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "BEACH_NOT_FOUND",
                "message", e.getMessage(),
                "timestamp", ZonedDateTime.now().toString()
        ));
    }

    @ExceptionHandler(CameraUnavailableException.class)
    public ResponseEntity<Map<String, Object>> handleCameraUnavailable(CameraUnavailableException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "CAMERA_NOT_FOUND",
                "message", e.getMessage(),
                "timestamp", ZonedDateTime.now().toString()
        ));
    }

    /**
     * Static assets and browser probes (favicon, etc.) should not be logged as application faults.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResource(NoResourceFoundException e) {
        log.debug("No resource: {}", e.getResourcePath());
        return ResponseEntity.notFound().build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "error", "INTERNAL_ERROR",
                "message", "An unexpected error occurred",
                "timestamp", ZonedDateTime.now().toString()
        ));
    }
}
