package com.fitflex.exception;

/**
 * Thrown when login credentials are wrong.
 * {@link GlobalExceptionHandler} turns it into a 401 Unauthorized JSON response.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }
}
