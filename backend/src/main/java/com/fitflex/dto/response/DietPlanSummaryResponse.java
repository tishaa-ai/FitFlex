package com.fitflex.dto.response;

import com.fitflex.entity.DietPlan;
import com.fitflex.entity.DietSource;
import com.fitflex.entity.FitnessGoal;

import java.time.LocalDateTime;

/** Short version of a diet plan (no meals), for the dashboard card and the history list. */
public record DietPlanSummaryResponse(
        Long id,
        FitnessGoal goal,
        String dietType,
        Integer mealsPerDay,
        Double bmr,
        Double tdee,
        Integer targetCalories,
        DietSource source,
        LocalDateTime createdAt) {

    public static DietPlanSummaryResponse from(DietPlan plan) {
        return new DietPlanSummaryResponse(plan.getId(), plan.getGoal(), plan.getDietType(), plan.getMealsPerDay(),
                plan.getBmr(), plan.getTdee(), plan.getTargetCalories(), plan.getSource(), plan.getCreatedAt());
    }
}
