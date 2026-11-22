package com.fitflex.dto.response;

import com.fitflex.entity.ActivityLevel;
import com.fitflex.entity.FitnessGoal;
import com.fitflex.entity.FitnessLevel;
import com.fitflex.entity.Gender;
import com.fitflex.entity.Role;
import com.fitflex.entity.User;

import java.time.LocalDateTime;

/** A user's profile as sent to the frontend. Notice: no password field, ever. */
public record UserProfileResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        String phone,
        Integer age,
        Gender gender,
        Double heightCm,
        Double weightKg,
        FitnessGoal fitnessGoal,
        FitnessLevel fitnessLevel,
        ActivityLevel activityLevel,
        LocalDateTime createdAt) {

    /** Copies the safe fields from the entity into the DTO. */
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(user.getId(), user.getFullName(), user.getEmail(), user.getRole(),
                user.getPhone(), user.getAge(), user.getGender(), user.getHeightCm(), user.getWeightKg(),
                user.getFitnessGoal(), user.getFitnessLevel(), user.getActivityLevel(), user.getCreatedAt());
    }
}
