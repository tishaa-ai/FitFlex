package com.fitflex.exception;

/**
 * Thrown when the request clashes with existing data (duplicate email, double booking, full group session).
 * {@link GlobalExceptionHandler} turns it into a 409 Conflict JSON response.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
