package com.fitflex.dto.response;

import com.fitflex.entity.AvailabilitySlot;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One weekly availability window of a trainer, e.g. MONDAY 07:00-11:00. */
public record AvailabilitySlotResponse(Long id, DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {

    public static AvailabilitySlotResponse from(AvailabilitySlot slot) {
        return new AvailabilitySlotResponse(slot.getId(), slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime());
    }
}
