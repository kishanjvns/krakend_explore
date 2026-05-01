package com.trucare.exception;

import java.time.Instant;

/**
 * Standardised error response shape — returned by GlobalExceptionHandler.
 *
 * Java 17 FEATURE — Record used as an error DTO.
 *
 * Using Instant for the timestamp:
 *   - Instant is always UTC — no timezone ambiguity.
 *   - Machine-readable ISO 8601 string when serialised (requires JavaTimeModule).
 *   - Never use LocalDateTime for API timestamps — it has no timezone info.
 *
 * Interview note:
 *   A consistent error shape is critical in microservices so that
 *   KrakenD (and calling clients) can parse errors predictably
 *   regardless of which backend service threw them.
 *   Inconsistent errors are one of the top microservices debugging pain points.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp
) {
    public static ErrorResponse of(int status, String error,
                                   String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now());
    }
}
