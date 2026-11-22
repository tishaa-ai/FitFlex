package com.fitflex.dto.response;

import java.util.List;

/** Everything the user dashboard page needs, in one request. */
public record UserDashboardResponse(
        String fullName,
        ProgressSummaryResponse summary,
        List<SessionResponse> upcomingSessions,
        DietPlanSummaryResponse latestDietPlan,
        List<WorkoutPlanResponse> workoutPlans,
        List<ResourceResponse> resources) {
}
