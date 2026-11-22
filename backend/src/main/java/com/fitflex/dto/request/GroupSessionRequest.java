package com.fitflex.dto.request;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Body of POST /api/sessions/group (a TRAINER creates a group class). */
public record GroupSessionRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 150)
        String title,

        @Size(max = 1000)
        String description,

        @NotNull(message = "Date and time are required")
        @Future(message = "Session time must be in the future")
        LocalDateTime scheduledAt,

        @NotNull(message = "Duration is required")
        @Min(value = 15, message = "Duration must be at least 15 minutes")
        @Max(value = 180, message = "Duration must be at most 180 minutes")
        Integer durationMinutes,

        @NotNull(message = "Maximum participants is required")
        @Min(value = 2, message = "A group needs at least 2 places")
        @Max(value = 100, message = "At most 100 participants")
        Integer maxParticipants) {
}
