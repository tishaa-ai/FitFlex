package com.fitflex.dto.request;

import com.fitflex.entity.ActivityLevel;
import com.fitflex.entity.FitnessGoal;
import com.fitflex.entity.FitnessLevel;
import com.fitflex.entity.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body of POST /api/user/register. Besides login details it collects the body measurements and
 * goals that the nutrition module needs to calculate BMR and calories.
 */
public record UserRegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Name can be at most 100 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        String email,

        @NotBlank(message = "Password is required")
        @Pattern(regexp = PasswordRules.REGEX, message = PasswordRules.MESSAGE)
        String password,

        @Pattern(regexp = "^[0-9+\\- ]{7,20}$", message = "Enter a valid phone number")
        String phone,

        @NotNull(message = "Age is required")
        @Min(value = 13, message = "You must be at least 13")
        @Max(value = 100, message = "Age must be 100 or less")
        Integer age,

        @NotNull(message = "Gender is required")
        Gender gender,

        @NotNull(message = "Height is required")
        @Positive(message = "Height must be a positive number")
        Double heightCm,

        @NotNull(message = "Weight is required")
        @Positive(message = "Weight must be a positive number")
        Double weightKg,

        @NotNull(message = "Fitness goal is required")
        FitnessGoal fitnessGoal,

        @NotNull(message = "Fitness level is required")
        FitnessLevel fitnessLevel,

        @NotNull(message = "Activity level is required")
        ActivityLevel activityLevel) {
}
