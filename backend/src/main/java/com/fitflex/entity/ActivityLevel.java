package com.fitflex.entity;

/**
 * Daily activity level of a user. Each level carries the standard multiplier used to
 * turn BMR (calories burned at rest) into TDEE (total calories burned per day).
 */
public enum ActivityLevel {
    SEDENTARY(1.2),      // desk job, little exercise
    LIGHT(1.375),        // light exercise 1-3 days/week
    MODERATE(1.55),      // moderate exercise 3-5 days/week
    ACTIVE(1.725),       // hard exercise 6-7 days/week
    VERY_ACTIVE(1.9);    // physical job or training twice a day

    private final double factor;

    ActivityLevel(double factor) {
        this.factor = factor;
    }

    public double getFactor() {
        return factor;
    }
}
