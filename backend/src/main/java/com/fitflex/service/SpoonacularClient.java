package com.fitflex.service;

import com.fitflex.dto.response.DietPlanResponse.Meal;
import com.fitflex.dto.response.FoodSearchResponse.FoodItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Talks to the Spoonacular food API (https://spoonacular.com/food-api). This class only does
 * HTTP + JSON parsing; deciding when to fall back is NutritionService's job. Any failure
 * (no key, network error, quota exceeded) is thrown as an exception.
 *
 * The API key is sent in the "x-api-key" header from the backend, so it never reaches the browser.
 */
@Component
public class SpoonacularClient {

    static final String ORIGIN = "Spoonacular";
    /** Spoonacular's day plan always returns these three meals, in this order. */
    private static final String[] DAY_PLAN_TYPES = {"BREAKFAST", "LUNCH", "DINNER"};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String baseUrl;

    public SpoonacularClient(RestClient externalApiClient, ObjectMapper objectMapper,
                             @Value("${fitflex.spoonacular.api-key:}") String apiKey,
                             @Value("${fitflex.spoonacular.base-url:https://api.spoonacular.com}") String baseUrl) {
        this.restClient = externalApiClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    /**
     * GET /mealplanner/generate?timeFrame=day&targetCalories=..&diet=..
     * returns 3 recipes; a second call to /recipes/informationBulk fetches each recipe's nutrition.
     */
    public List<Meal> generateDayPlan(int targetCalories, String diet) {
        StringBuilder url = new StringBuilder(baseUrl)
                .append("/mealplanner/generate?timeFrame=day&targetCalories=").append(targetCalories);
        if (diet != null) {
            url.append("&diet=").append(diet);
        }
        JsonNode plan = get(url.toString());

        List<JsonNode> recipes = new ArrayList<>();
        plan.path("meals").forEach(recipes::add);
        if (recipes.isEmpty()) {
            throw new IllegalStateException("Spoonacular returned no meals");
        }

        Map<Long, JsonNode> nutritionById = fetchNutrition(recipes);
        JsonNode dayNutrients = plan.path("nutrients");

        List<Meal> meals = new ArrayList<>();
        for (int i = 0; i < recipes.size() && i < DAY_PLAN_TYPES.length; i++) {
            JsonNode recipe = recipes.get(i);
            long id = recipe.path("id").asLong();
            String type = DAY_PLAN_TYPES[i];

            int calories;
            double protein;
            double carbs;
            double fat;
            JsonNode nutrients = nutritionById.get(id);
            if (nutrients != null) {
                calories = (int) Math.round(nutrient(nutrients, "Calories"));
                protein = round1(nutrient(nutrients, "Protein"));
                carbs = round1(nutrient(nutrients, "Carbohydrates"));
                fat = round1(nutrient(nutrients, "Fat"));
            } else {
                // Per-recipe nutrition missing: share the day's totals equally (an estimate).
                int count = recipes.size();
                calories = (int) Math.round(dayNutrients.path("calories").asDouble() / count);
                protein = round1(dayNutrients.path("protein").asDouble() / count);
                carbs = round1(dayNutrients.path("carbohydrates").asDouble() / count);
                fat = round1(dayNutrients.path("fat").asDouble() / count);
            }

            String imageType = recipe.path("imageType").asString("jpg");
            String imageUrl = "https://img.spoonacular.com/recipes/" + id + "-312x231." + imageType;
            meals.add(new Meal(type, recipe.path("title").asString(), "1 serving", calories, protein, carbs, fat,
                    imageUrl, recipe.path("sourceUrl").asString(null), recipe.path("readyInMinutes").asInt(), ORIGIN));
        }
        return meals;
    }

    /**
     * Food search: /food/ingredients/search finds matching ingredients, then
     * /food/ingredients/{id}/information gives nutrition for 100 g of each.
     */
    public List<FoodItem> searchFood(String query, int limit) {
        String url = baseUrl + "/food/ingredients/search?number=" + limit + "&query="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        JsonNode results = get(url).path("results");

        List<FoodItem> items = new ArrayList<>();
        for (JsonNode result : results) {
            long id = result.path("id").asLong();
            JsonNode info = get(baseUrl + "/food/ingredients/" + id + "/information?amount=100&unit=grams");
            JsonNode nutrients = info.path("nutrition").path("nutrients");
            items.add(new FoodItem(capitalize(info.path("name").asString(result.path("name").asString())), "100 g",
                    (int) Math.round(nutrient(nutrients, "Calories")),
                    round1(nutrient(nutrients, "Protein")),
                    round1(nutrient(nutrients, "Carbohydrates")),
                    round1(nutrient(nutrients, "Fat"))));
        }
        return items;
    }

    /** Returns recipeId -> nutrients array; empty map if the call fails (the caller then estimates). */
    private Map<Long, JsonNode> fetchNutrition(List<JsonNode> recipes) {
        String ids = recipes.stream().map(r -> String.valueOf(r.path("id").asLong())).collect(Collectors.joining(","));
        Map<Long, JsonNode> byId = new HashMap<>();
        try {
            JsonNode bulk = get(baseUrl + "/recipes/informationBulk?includeNutrition=true&ids=" + ids);
            for (JsonNode recipe : bulk) {
                byId.put(recipe.path("id").asLong(), recipe.path("nutrition").path("nutrients"));
            }
        } catch (RuntimeException e) {
            // Not fatal: we still have titles and the day totals.
        }
        return byId;
    }

    private JsonNode get(String url) {
        if (!isConfigured()) {
            throw new IllegalStateException("SPOONACULAR_API_KEY is not set");
        }
        String body = restClient.get()
                .uri(url)
                .header("x-api-key", apiKey)
                .retrieve()          // throws for 4xx/5xx, e.g. 402 when the daily quota is used up
                .body(String.class);
        return objectMapper.readTree(body);
    }

    /** Finds e.g. {"name":"Protein","amount":21.3,"unit":"g"} in a Spoonacular nutrients array. */
    private static double nutrient(JsonNode nutrients, String name) {
        for (JsonNode n : nutrients) {
            if (name.equalsIgnoreCase(n.path("name").asString())) {
                return n.path("amount").asDouble();
            }
        }
        return 0;
    }

    private static String capitalize(String s) {
        return s == null || s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
