package com.fitflex.service;

import com.fitflex.dto.request.WorkoutPlanRequest;
import com.fitflex.dto.response.WorkoutPlanResponse;
import com.fitflex.entity.Role;
import com.fitflex.entity.Trainer;
import com.fitflex.entity.User;
import com.fitflex.entity.WorkoutExercise;
import com.fitflex.entity.WorkoutPlan;
import com.fitflex.exception.BadRequestException;
import com.fitflex.exception.ForbiddenException;
import com.fitflex.repository.WorkoutPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Trainers write workout plans for their clients; users see the plans assigned to them. */
@Service
public class WorkoutPlanService {

    private final WorkoutPlanRepository workoutPlanRepository;
    private final TrainerService trainerService;
    private final UserService userService;

    public WorkoutPlanService(WorkoutPlanRepository workoutPlanRepository, TrainerService trainerService,
                              UserService userService) {
        this.workoutPlanRepository = workoutPlanRepository;
        this.trainerService = trainerService;
        this.userService = userService;
    }

    @Transactional
    public WorkoutPlanResponse create(Long trainerUserId, WorkoutPlanRequest request) {
        Trainer trainer = trainerService.getTrainerByUserId(trainerUserId);
        User client = userService.getUserEntity(request.userId());

        if (client.getRole() != Role.USER) {
            throw new BadRequestException("Workout plans can only be assigned to users");
        }
        // Why: a trainer must not push plans to strangers, only to people who booked them.
        if (!trainerService.isClient(trainer.getId(), client.getId())) {
            throw new ForbiddenException("You can only create plans for your own clients");
        }

        WorkoutPlan plan = new WorkoutPlan();
        plan.setTrainer(trainer);
        plan.setUser(client);
        plan.setTitle(request.title().trim());
        plan.setGoal(request.goal());
        plan.setDurationWeeks(request.durationWeeks());
        for (WorkoutPlanRequest.ExerciseRequest row : request.exercises()) {
            WorkoutExercise exercise = new WorkoutExercise();
            exercise.setName(row.name().trim());
            exercise.setSets(row.sets());
            exercise.setReps(row.reps());
            exercise.setRestSeconds(row.restSeconds() != null ? row.restSeconds() : 60);
            exercise.setDayNumber(row.dayNumber());
            exercise.setNotes(row.notes());
            plan.addExercise(exercise);
        }
        return WorkoutPlanResponse.from(workoutPlanRepository.save(plan));
    }

    /** USER: plans assigned to me. TRAINER: plans I created. */
    @Transactional(readOnly = true)
    public List<WorkoutPlanResponse> getMyPlans(Long userId, Role role) {
        List<WorkoutPlan> plans = role == Role.TRAINER
                ? workoutPlanRepository.findByTrainerIdOrderByCreatedAtDesc(trainerService.getTrainerByUserId(userId).getId())
                : workoutPlanRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return plans.stream().map(WorkoutPlanResponse::from).toList();
    }
}
