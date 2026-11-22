package com.fitflex.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Body of POST /api/workout-plans: a trainer writes a plan for one of their clients. */
public record WorkoutPlanRequest(
        @NotNull(message = "Choose a client")
        Long userId,

        @NotBlank(message = "Title is required")
        @Size(max = 150)
        String title,

        @Size(max = 100)
        String goal,

        @NotNull(message = "Duration is required")
        @Min(value = 1, message = "At least 1 week")
        @Max(value = 52, message = "At most 52 weeks")
        Integer durationWeeks,

        /** @Valid makes Bean Validation also check every exercise in the list. */
        @NotEmpty(message = "Add at least one exercise")
        @Valid
        List<ExerciseRequest> exercises) {

    /** One exercise row from the dynamic form. */
    public record ExerciseRequest(
            @NotBlank(message = "Exercise name is required")
            @Size(max = 100)
            String name,

            @NotNull @Min(value = 1, message = "Sets must be at least 1") @Max(20)
            Integer sets,

            @NotNull @Min(value = 1, message = "Reps must be at least 1") @Max(200)
            Integer reps,

            @Min(value = 0, message = "Rest cannot be negative") @Max(600)
            Integer restSeconds,

            @NotNull @Min(value = 1, message = "Day must be 1 or more") @Max(7)
            Integer dayNumber,

            @Size(max = 500)
            String notes) {
    }
}
