package com.fitflex.dto.request;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** Body of POST /api/trainer/availability, e.g. {"dayOfWeek":"MONDAY","startTime":"07:00","endTime":"10:00"}. */
public record AvailabilityRequest(
        @NotNull(message = "Day is required")
        DayOfWeek dayOfWeek,

        @NotNull(message = "Start time is required")
        LocalTime startTime,

        @NotNull(message = "End time is required")
        LocalTime endTime) {
}
