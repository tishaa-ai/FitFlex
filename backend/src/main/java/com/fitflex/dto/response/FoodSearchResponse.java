package com.fitflex.dto.response;

import java.util.List;

/** Result of GET /api/diet/search-food. {@code source} is "SPOONACULAR" or "LOCAL_FALLBACK". */
public record FoodSearchResponse(String source, String message, List<FoodItem> items) {

    /** Nutrition of one food for the given serving (Spoonacular and the local dataset both use 100 g). */
    public record FoodItem(String name, String servingSize, int calories, double protein, double carbs, double fat) {
    }
}
