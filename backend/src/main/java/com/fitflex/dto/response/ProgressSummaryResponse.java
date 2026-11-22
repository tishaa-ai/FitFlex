package com.fitflex.dto.response;

/**
 * Headline numbers for the progress page and user dashboard.
 *
 * @param currentStreak       consecutive days (ending today or yesterday) with at least one log
 * @param totalWorkouts       number of distinct days with workout minutes logged
 * @param weightChangeKg      latest weight minus first logged weight (negative = lost weight); null if not enough data
 * @param averageFormAccuracy average correct-form % over all pose detection sessions; null if none
 */
public record ProgressSummaryResponse(
        int currentStreak,
        int totalWorkouts,
        int workoutsThisWeek,
        int totalWorkoutMinutes,
        int totalCaloriesBurned,
        Double startWeightKg,
        Double currentWeightKg,
        Double weightChangeKg,
        int poseSessions,
        int totalPoseReps,
        Double averageFormAccuracy) {
}
