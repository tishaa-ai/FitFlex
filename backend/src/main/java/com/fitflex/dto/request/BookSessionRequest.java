package com.fitflex.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * Body of POST /api/sessions/book (a USER books a one-on-one).
 * Example: {"trainerId":1,"scheduledAt":"2026-12-02T08:00","durationMinutes":60,"title":"Form check"}
 */
public record BookSessionRequest(
        @NotNull(message = "Trainer is required")
        Long trainerId,

        @NotNull(message = "Date and time are required")
        @Future(message = "Session time must be in the future")
        LocalDateTime scheduledAt,

        /** Optional, defaults to 60 minutes. */
        @Min(value = 30, message = "Sessions are at least 30 minutes")
        @Max(value = 120, message = "Sessions are at most 120 minutes")
        Integer durationMinutes,

        @Size(max = 150)
        String title,

        @Size(max = 1000)
        String description) {
}
