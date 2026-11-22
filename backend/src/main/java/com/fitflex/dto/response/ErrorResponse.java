package com.fitflex.dto.response;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The single JSON shape used for every error the API returns, e.g.
 * {"timestamp": "...", "status": 400, "message": "Validation failed", "errors": {"email": "must be a valid email"}}.
 *
 * DTOs in FitFlex are Java records: a record is a small immutable class where Java itself
 * writes the constructor, getters (here: timestamp(), status() ...), equals and toString.
 */
public record ErrorResponse(LocalDateTime timestamp, int status, String message, Map<String, String> errors) {

    /** Convenience factory for errors without field details. */
    public static ErrorResponse of(int status, String message) {
        return new ErrorResponse(LocalDateTime.now(), status, message, Map.of());
    }
}
