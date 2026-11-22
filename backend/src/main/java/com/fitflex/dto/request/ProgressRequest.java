package com.fitflex.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/** Body of POST /api/progress (manual log). The service requires weight or workout minutes. */
public record ProgressRequest(
        /** Optional, defaults to today. */
        @PastOrPresent(message = "Date cannot be in the future")
        LocalDate date,

        @Positive(message = "Weight must be a positive number")
        @Max(value = 400, message = "Weight looks too high")
        Double weightKg,

        @Min(value = 0, message = "Minutes cannot be negative")
        @Max(value = 600, message = "That is more than 10 hours")
        Integer workoutMinutes,

        @Min(value = 0, message = "Calories cannot be negative")
        @Max(value = 10000)
        Integer caloriesBurned,

        @Size(max = 500)
        String notes) {
}
