package com.fitflex.dto.request;

import com.fitflex.entity.SessionStatus;
import jakarta.validation.constraints.NotNull;

/** Body of PUT /api/sessions/{id}/status, e.g. {"status":"CONFIRMED"}. */
public record SessionStatusRequest(
        @NotNull(message = "Status is required")
        SessionStatus status) {
}
