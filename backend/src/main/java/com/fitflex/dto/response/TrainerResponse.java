package com.fitflex.dto.response;

import com.fitflex.entity.AvailabilitySlot;
import com.fitflex.entity.Trainer;

import java.math.BigDecimal;
import java.util.List;

/**
 * A trainer profile as shown on the trainer cards, the profile page and the trainer's own dashboard.
 * Email and phone are only filled in for the trainer themself (public listings leave them null).
 */
public record TrainerResponse(
        Long id,
        Long userId,
        String fullName,
        String email,
        String phone,
        String specialization,
        Integer experienceYears,
        String certifications,
        String bio,
        BigDecimal pricePerSession,
        Double rating,
        String profileImageUrl,
        boolean verified,
        List<AvailabilitySlotResponse> availability) {

    /** Public view: no contact details. */
    public static TrainerResponse publicView(Trainer trainer) {
        return build(trainer, false);
    }

    /** The trainer looking at their own profile. */
    public static TrainerResponse ownerView(Trainer trainer) {
        return build(trainer, true);
    }

    private static TrainerResponse build(Trainer trainer, boolean includeContact) {
        List<AvailabilitySlotResponse> slots = trainer.getAvailability().stream()
                .sorted(AvailabilitySlot.WEEK_ORDER)
                .map(AvailabilitySlotResponse::from)
                .toList();
        return new TrainerResponse(trainer.getId(), trainer.getUser().getId(), trainer.getUser().getFullName(),
                includeContact ? trainer.getUser().getEmail() : null,
                includeContact ? trainer.getUser().getPhone() : null,
                trainer.getSpecialization(), trainer.getExperienceYears(), trainer.getCertifications(),
                trainer.getBio(), trainer.getPricePerSession(), trainer.getRating(), trainer.getProfileImageUrl(),
                trainer.isVerified(), slots);
    }
}
