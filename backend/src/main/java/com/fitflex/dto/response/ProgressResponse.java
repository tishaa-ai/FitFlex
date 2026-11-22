package com.fitflex.dto.response;

import com.fitflex.entity.ProgressEntry;
import com.fitflex.entity.ProgressSource;

import java.time.LocalDate;

/** One progress log entry (manual or from pose detection). */
public record ProgressResponse(
        Long id,
        LocalDate date,
        Double weightKg,
        Integer workoutMinutes,
        Integer caloriesBurned,
        String notes,
        ProgressSource source,
        String exercise,
        Integer reps,
        Double correctFormPercent) {

    public static ProgressResponse from(ProgressEntry e) {
        return new ProgressResponse(e.getId(), e.getDate(), e.getWeightKg(), e.getWorkoutMinutes(),
                e.getCaloriesBurned(), e.getNotes(), e.getSource(), e.getExercise(), e.getReps(),
                e.getCorrectFormPercent());
    }
}
