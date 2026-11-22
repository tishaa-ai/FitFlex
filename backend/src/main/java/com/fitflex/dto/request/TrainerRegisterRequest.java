package com.fitflex.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Body of POST /api/trainer/add (trainer registration): account details plus the trainer profile. */
public record TrainerRegisterRequest(
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

        @NotBlank(message = "Specialization is required")
        @Size(max = 50)
        String specialization,

        @NotNull(message = "Experience is required")
        @Min(value = 0, message = "Experience cannot be negative")
        @Max(value = 60, message = "Experience looks too high")
        Integer experienceYears,

        @Size(max = 500)
        String certifications,

        @Size(max = 2000)
        String bio,

        @NotNull(message = "Price per session is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be a positive number")
        BigDecimal pricePerSession,

        @Size(max = 255)
        String profileImageUrl) {
}
