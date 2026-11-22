package com.fitflex.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/progress/pose, sent by the Python pose detection script when the user quits.
 * Example: {"exercise":"SQUAT","reps":20,"correctFormPercent":85.5,"durationSeconds":300}
 */
public record PoseSessionRequest(
        @NotBlank(message = "Exercise is required")
        @Size(max = 50)
        String exercise,

        @NotNull(message = "Reps are required")
        @Min(value = 0) @Max(value = 10000)
        Integer reps,

        @NotNull(message = "Correct form percentage is required")
        @DecimalMin("0.0") @DecimalMax("100.0")
        Double correctFormPercent,

        @NotNull(message = "Duration is required")
        @Min(value = 0) @Max(value = 86400)
        Integer durationSeconds,

        /** Optional: if missing the server estimates it. */
        @Min(value = 0) @Max(value = 10000)
        Integer caloriesBurned,

        @Size(max = 500)
        String notes) {
}
