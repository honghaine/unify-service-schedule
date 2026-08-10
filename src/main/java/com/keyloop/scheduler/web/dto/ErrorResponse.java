package com.keyloop.scheduler.web.dto;

import java.time.Instant;
import java.util.List;

public record ErrorResponse(
        int status,
        String error,
        String message,
        String correlationId,
        Instant timestamp,
        List<String> fieldErrors) {

    public static ErrorResponse of(int status, String error, String message, String correlationId) {
        return new ErrorResponse(status, error, message, correlationId, Instant.now(), List.of());
    }

    public static ErrorResponse ofFieldErrors(
            int status, String error, String message, String correlationId, List<String> fieldErrors) {
        return new ErrorResponse(status, error, message, correlationId, Instant.now(), fieldErrors);
    }
}
