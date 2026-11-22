package com.fitflex.dto.request;

import com.fitflex.entity.ActivityLevel;
import com.fitflex.entity.FitnessGoal;
import com.fitflex.entity.FitnessLevel;
import com.fitflex.entity.Gender;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of PUT /api/user/profile. Email and password are not editable here.
 * Fields left null are simply not changed.
 */
public record UserProfileUpdateRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        String fullName,

        @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "Enter a valid phone number")
        String phone,

        @Min(value = 13, message = "You must be at least 13")
        @Max(value = 100, message = "Age must be 100 or less")
        Integer age,

        Gender gender,

        @Positive(message = "Height must be a positive number")
        Double heightCm,

        @Positive(message = "Weight must be a positive number")
        Double weightKg,

        FitnessGoal fitnessGoal,
        FitnessLevel fitnessLevel,
        ActivityLevel activityLevel) {
}
