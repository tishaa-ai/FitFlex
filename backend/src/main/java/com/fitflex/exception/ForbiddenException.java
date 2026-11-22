package com.fitflex.exception;

/**
 * Thrown when a logged-in user tries to touch data that is not theirs.
 * {@link GlobalExceptionHandler} turns it into a 403 Forbidden JSON response.
 */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
