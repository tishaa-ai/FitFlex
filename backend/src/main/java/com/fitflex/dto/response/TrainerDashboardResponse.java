package com.fitflex.dto.response;

import java.util.List;

/** Everything the trainer dashboard needs at first load, in one request. */
public record TrainerDashboardResponse(
        TrainerResponse profile,
        int upcomingSessionCount,
        int pendingRequestCount,
        int clientCount,
        int resourceCount,
        List<SessionResponse> upcomingSessions,
        List<SessionResponse> pendingRequests,
        List<UserProfileResponse> clients) {
}
