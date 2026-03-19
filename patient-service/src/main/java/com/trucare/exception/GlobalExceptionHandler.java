package com.trucare.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Spring Boot CONCEPT — @RestControllerAdvice
 *
 * Centralises exception handling across ALL @RestController classes.
 * Without this, every controller method would need its own try-catch blocks.
 *
 * @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 * The @ResponseBody means every handler method's return value is
 * automatically serialised to JSON — no need for ResponseEntity wrapping.
 *
 * Spring Boot EVOLUTION:
 *   Spring 3.x : @ExceptionHandler only worked per-controller
 *   Spring 4.x : @ControllerAdvice introduced — global exception handling
 *   Spring 4.x : @RestControllerAdvice added as convenience combination
 *
 * How it works with KrakenD:
 *   KrakenD's backend receives the HTTP status code your service returns.
 *   404 → KrakenD can return a partial response (aggregation resilience, Step 3)
 *   500 → KrakenD can trigger the circuit breaker (Step 5)
 *   Consistent error shapes make this behaviour predictable and configurable.
 *
 * Interview note:
 *   @RestControllerAdvice is a cross-cutting concern — a perfect example of
 *   where Spring AOP (via proxies) applies advice across multiple beans
 *   without modifying those beans directly.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PatientNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handlePatientNotFound(
            PatientNotFoundException ex,
            HttpServletRequest request) {

        return ErrorResponse.of(
                HttpStatus.NOT_FOUND.value(),
                "NOT_FOUND",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        return ErrorResponse.of(
                HttpStatus.BAD_REQUEST.value(),
                "BAD_REQUEST",
                ex.getMessage(),
                request.getRequestURI()
        );
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneral(
            Exception ex,
            HttpServletRequest request) {

        return ErrorResponse.of(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                request.getRequestURI()
        );
    }
}
