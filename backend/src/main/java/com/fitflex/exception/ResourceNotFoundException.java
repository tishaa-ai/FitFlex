package com.fitflex.exception;

/**
 * Thrown when a requested record (trainer, session, file ...) does not exist.
 * {@link GlobalExceptionHandler} turns it into a 404 Not Found JSON response.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
