package com.fitflex.dto.request;

import com.fitflex.entity.FitnessGoal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Body of POST /api/diet/generate, e.g. {"goal":"WEIGHT_LOSS","dietType":"vegetarian","mealsPerDay":4}.
 * Age, gender, height, weight and activity level are taken from the user's profile.
 */
public record DietGenerateRequest(
        /** Optional: defaults to the goal saved in the profile. */
        FitnessGoal goal,

        @NotBlank(message = "Diet type is required")
        String dietType,

        /** 3 = breakfast, lunch, dinner; 4 adds an evening snack; 5 adds a morning snack too. Defaults to 3. */
        @Min(value = 3, message = "At least 3 meals per day")
        @Max(value = 5, message = "At most 5 meals per day")
        Integer mealsPerDay) {
}
