package com.fitflex.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of POST /api/contact (public Contact Us form). */
public record ContactRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100)
        String name,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150)
        String email,

        @Size(max = 150)
        String subject,

        @NotBlank(message = "Message is required")
        @Size(min = 10, max = 2000, message = "Message must be 10 to 2000 characters")
        String message) {
}
