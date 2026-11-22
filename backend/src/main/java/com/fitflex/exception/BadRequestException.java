package com.fitflex.exception;

/**
 * Thrown when the request is understandable but breaks a business rule (e.g. booking in the past).
 * {@link GlobalExceptionHandler} turns it into a 400 Bad Request JSON response.
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
