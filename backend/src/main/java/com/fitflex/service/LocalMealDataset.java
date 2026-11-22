package com.fitflex.service;

import com.fitflex.dto.response.DietPlanResponse.Meal;
import com.fitflex.dto.response.FoodSearchResponse.FoodItem;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The offline fallback for nutrition: loads {@code fallback/meals.json} (about 40 Indian-friendly
 * meals) and {@code fallback/foods.json} (common foods per 100 g) once at startup, then picks
 * meals for a calorie target and searches foods by name.
 */
@Component
public class LocalMealDataset {

    static final String ORIGIN = "FitFlex dataset";

    /** One row of meals.json. */
    record DatasetMeal(String name, String mealType, String servingSize, DietType diet,
                       int calories, double protein, double carbs, double fat) {
    }

    private final List<DatasetMeal> meals = new ArrayList<>();
    private final List<FoodItem> foods = new ArrayList<>();

    public LocalMealDataset(ObjectMapper objectMapper) {
        JsonNode mealsJson = read(objectMapper, "fallback/meals.json");
        for (JsonNode m : mealsJson.path("meals")) {
            meals.add(new DatasetMeal(m.path("name").asString(), m.path("mealType").asString(),
                    m.path("servingSize").asString(), DietType.valueOf(m.path("diet").asString()),
                    m.path("calories").asInt(), m.path("protein").asDouble(),
                    m.path("carbs").asDouble(), m.path("fat").asDouble()));
        }
        JsonNode foodsJson = read(objectMapper, "fallback/foods.json");
        for (JsonNode f : foodsJson.path("foods")) {
            foods.add(new FoodItem(f.path("name").asString(), f.path("servingSize").asString(),
                    f.path("calories").asInt(), f.path("protein").asDouble(),
                    f.path("carbs").asDouble(), f.path("fat").asDouble()));
        }
    }

    /**
     * Picks one meal of the given type that suits the diet and scales its portion so the
     * calories land close to {@code targetCalories}.
     *
     * @param alreadyUsed names already in today's plan, so two snacks are not the same dish
     */
    public Meal pickMeal(String mealType, DietType diet, int targetCalories, Set<String> alreadyUsed) {
        List<DatasetMeal> candidates = meals.stream()
                .filter(m -> m.mealType().equals(mealType))
                .filter(m -> diet.allows(m.diet()))
                .filter(m -> !alreadyUsed.contains(m.name()))
                .sorted(Comparator.comparingInt(m -> score(m, diet, targetCalories)))
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No " + mealType + " meals for diet " + diet + " in meals.json");
        }

        // Choose randomly among the 3 closest matches, so generating twice gives some variety.
        int choices = Math.min(3, candidates.size());
        DatasetMeal chosen = candidates.get(ThreadLocalRandom.current().nextInt(choices));

        // Scale the portion in steps of 0.25, between half and double a serving.
        double portion = Math.round((double) targetCalories / chosen.calories() * 4) / 4.0;
        portion = Math.max(0.5, Math.min(2.0, portion));

        String serving = portion == 1.0 ? chosen.servingSize() : formatPortion(portion) + " x " + chosen.servingSize();
        return new Meal(mealType, chosen.name(), serving,
                (int) Math.round(chosen.calories() * portion),
                round1(chosen.protein() * portion), round1(chosen.carbs() * portion), round1(chosen.fat() * portion),
                null, null, null, ORIGIN);
    }

    /**
     * Lower score = better match: the calorie gap, with a 150 kcal bonus for meals of exactly the
     * chosen diet. Why: otherwise a non-veg user could get only paneer dishes, since vegetarian
     * meals are also "allowed" for them.
     */
    private static int score(DatasetMeal meal, DietType diet, int targetCalories) {
        int gap = Math.abs(meal.calories() - targetCalories);
        return meal.diet() == diet ? gap - 150 : gap;
    }

    /** Case-insensitive name search across foods (per 100 g) and ready meals (per serving). */
    public List<FoodItem> searchFoods(String query, int limit) {
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<FoodItem> result = new ArrayList<>();
        foods.stream().filter(f -> f.name().toLowerCase(Locale.ROOT).contains(q)).forEach(result::add);
        meals.stream()
                .filter(m -> m.name().toLowerCase(Locale.ROOT).contains(q))
                .map(m -> new FoodItem(m.name(), m.servingSize(), m.calories(), m.protein(), m.carbs(), m.fat()))
                .forEach(result::add);
        return result.stream().limit(limit).toList();
    }

    private static JsonNode read(ObjectMapper objectMapper, String path) {
        try (InputStream in = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read " + path, e);
        }
    }

    private static String formatPortion(double portion) {
        return portion == Math.floor(portion) ? String.valueOf((int) portion) : String.valueOf(portion);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
