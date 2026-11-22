package com.fitflex.dto.response;

import com.fitflex.entity.WorkoutExercise;
import com.fitflex.entity.WorkoutPlan;

import java.time.LocalDateTime;
import java.util.List;

/** A workout plan with all its exercises, ordered by day. */
public record WorkoutPlanResponse(
        Long id,
        String title,
        String goal,
        Integer durationWeeks,
        LocalDateTime createdAt,
        Long trainerId,
        String trainerName,
        Long userId,
        String userName,
        List<Exercise> exercises) {

    /** One exercise line inside the plan. */
    public record Exercise(Long id, Integer dayNumber, String name, Integer sets, Integer reps,
                           Integer restSeconds, String notes) {

        static Exercise from(WorkoutExercise e) {
            return new Exercise(e.getId(), e.getDayNumber(), e.getName(), e.getSets(), e.getReps(),
                    e.getRestSeconds(), e.getNotes());
        }
    }

    public static WorkoutPlanResponse from(WorkoutPlan plan) {
        return new WorkoutPlanResponse(plan.getId(), plan.getTitle(), plan.getGoal(), plan.getDurationWeeks(),
                plan.getCreatedAt(), plan.getTrainer().getId(), plan.getTrainer().getUser().getFullName(),
                plan.getUser().getId(), plan.getUser().getFullName(),
                plan.getExercises().stream().map(Exercise::from).toList());
    }
}
