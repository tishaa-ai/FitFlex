package com.fitflex.service;

import com.fitflex.exception.BadRequestException;

import java.util.Locale;

/**
 * The four diet preferences offered on the nutrition page, ordered from strictest to most
 * relaxed: a vegan meal is fine for a vegetarian, a vegetarian meal is fine for an eggetarian,
 * and everything is fine for non-veg. {@link #allows(DietType)} uses that order.
 */
public enum DietType {
    VEGAN("vegan", "vegan"),
    VEGETARIAN("vegetarian", "vegetarian"),
    // Spoonacular's "vegetarian" allows eggs (no meat or fish), which is exactly "eggetarian".
    EGGETARIAN("eggetarian", "vegetarian"),
    // null = no diet filter at Spoonacular.
    NON_VEG("non-veg", null);

    private final String label;
    private final String spoonacularDiet;

    DietType(String label, String spoonacularDiet) {
        this.label = label;
        this.spoonacularDiet = spoonacularDiet;
    }

    public String getLabel() {
        return label;
    }

    public String getSpoonacularDiet() {
        return spoonacularDiet;
    }

    /** True if a meal tagged {@code mealDiet} may be served to someone following this diet. */
    public boolean allows(DietType mealDiet) {
        return mealDiet.ordinal() <= this.ordinal();
    }

    /** Accepts "vegan", "Vegetarian", "eggetarian", "non-veg", "non_veg", "NON_VEG" ... */
    public static DietType fromLabel(String value) {
        if (value != null) {
            String normalized = value.trim().toLowerCase(Locale.ROOT).replace('_', '-').replace(' ', '-');
            for (DietType type : values()) {
                if (type.label.equals(normalized)) {
                    return type;
                }
            }
        }
        throw new BadRequestException("Diet type must be one of: vegetarian, vegan, eggetarian, non-veg");
    }
}
