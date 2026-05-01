package com.trucare.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot CONCEPT — @RestControllerAdvice
 * Identical pattern to patient-service GlobalExceptionHandler.
 * Consistent error handling across all services in a system
 * is a production best practice — KrakenD can reliably interpret errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ReferralNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleReferralNotFound(
            ReferralNotFoundException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(404, "NOT_FOUND",
                ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {
        return ErrorResponse.of(400, "BAD_REQUEST",
                ex.getMessage(), request.getRequestURI());
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(
            Exception ex,
            HttpServletRequest request) {
        return ErrorResponse.of(500, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred", request.getRequestURI());
    }
}
