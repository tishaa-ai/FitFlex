package com.fitflex.service;

import com.fitflex.dto.response.DietPlanSummaryResponse;
import com.fitflex.dto.response.SessionResponse;
import com.fitflex.dto.response.TrainerDashboardResponse;
import com.fitflex.dto.response.TrainerResponse;
import com.fitflex.dto.response.UserDashboardResponse;
import com.fitflex.dto.response.UserProfileResponse;
import com.fitflex.entity.Role;
import com.fitflex.entity.SessionStatus;
import com.fitflex.entity.Trainer;
import com.fitflex.repository.DietPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Collects the data for the two dashboard pages from the other services, so each dashboard
 * loads with a single API call. Contains no business rules of its own.
 */
@Service
public class DashboardService {

    private final UserService userService;
    private final TrainerService trainerService;
    private final SessionService sessionService;
    private final ProgressService progressService;
    private final WorkoutPlanService workoutPlanService;
    private final ResourceService resourceService;
    private final DietPlanRepository dietPlanRepository;

    public DashboardService(UserService userService, TrainerService trainerService, SessionService sessionService,
                            ProgressService progressService, WorkoutPlanService workoutPlanService,
                            ResourceService resourceService, DietPlanRepository dietPlanRepository) {
        this.userService = userService;
        this.trainerService = trainerService;
        this.sessionService = sessionService;
        this.progressService = progressService;
        this.workoutPlanService = workoutPlanService;
        this.resourceService = resourceService;
        this.dietPlanRepository = dietPlanRepository;
    }

    @Transactional(readOnly = true)
    public UserDashboardResponse forUser(Long userId) {
        String name = userService.getUserEntity(userId).getFullName();
        DietPlanSummaryResponse latestDiet = dietPlanRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)
                .map(DietPlanSummaryResponse::from)
                .orElse(null);
        List<SessionResponse> upcoming = sessionService.getMySessions(userId, Role.USER, "upcoming");

        return new UserDashboardResponse(
                name,
                progressService.getSummary(userId),
                upcoming.stream().limit(5).toList(),
                latestDiet,
                workoutPlanService.getMyPlans(userId, Role.USER),
                resourceService.getMyResources(userId, Role.USER));
    }

    @Transactional(readOnly = true)
    public TrainerDashboardResponse forTrainer(Long trainerUserId) {
        Trainer trainer = trainerService.getTrainerByUserId(trainerUserId);
        List<SessionResponse> upcoming = sessionService.getMySessions(trainerUserId, Role.TRAINER, "upcoming");
        List<SessionResponse> confirmedUpcoming = upcoming.stream()
                .filter(s -> s.status() == SessionStatus.CONFIRMED).toList();
        List<SessionResponse> pending = upcoming.stream()
                .filter(s -> s.status() == SessionStatus.REQUESTED).toList();
        List<UserProfileResponse> clients = trainerService.getMyClients(trainerUserId);
        int resourceCount = resourceService.getMyResources(trainerUserId, Role.TRAINER).size();

        return new TrainerDashboardResponse(
                TrainerResponse.ownerView(trainer),
                confirmedUpcoming.size(),
                pending.size(),
                clients.size(),
                resourceCount,
                confirmedUpcoming,
                pending,
                clients);
    }
}
