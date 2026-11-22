package com.fitflex.dto.response;

import com.fitflex.entity.DietSource;
import com.fitflex.entity.FitnessGoal;

import java.time.LocalDateTime;
import java.util.List;

/**
 * A full diet plan: the calorie maths (BMR, TDEE, target) plus the meals grouped by meal type
 * and the daily totals. {@code note} explains where the meals came from (e.g. fallback reason).
 */
public record DietPlanResponse(
        Long id,
        FitnessGoal goal,
        String dietType,
        Integer mealsPerDay,
        Double bmr,
        Double tdee,
        Integer targetCalories,
        DietSource source,
        String note,
        LocalDateTime createdAt,
        List<MealGroup> mealGroups,
        Nutrients totals) {

    /** All meals of one type, e.g. "SNACK" may hold two snacks on a 5-meal plan. */
    public record MealGroup(String mealType, List<Meal> meals) {
    }

    /**
     * One meal with its nutrition. {@code origin} says whether it came from Spoonacular or the
     * FitFlex dataset (a Spoonacular plan can still contain local snacks).
     */
    public record Meal(String mealType, String name, String servingSize, int calories,
                       double protein, double carbs, double fat,
                       String imageUrl, String sourceUrl, Integer readyInMinutes, String origin) {
    }

    /** Calories in kcal, macros in grams. */
    public record Nutrients(int calories, double protein, double carbs, double fat) {
    }
}
