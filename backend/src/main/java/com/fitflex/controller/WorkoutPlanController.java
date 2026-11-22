package com.fitflex.controller;

import com.fitflex.dto.request.WorkoutPlanRequest;
import com.fitflex.dto.response.WorkoutPlanResponse;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.WorkoutPlanService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Workout plans: TRAINER creates for a client, both roles list their own. */
@RestController
@RequestMapping("/api/workout-plans")
public class WorkoutPlanController {

    private final WorkoutPlanService workoutPlanService;

    public WorkoutPlanController(WorkoutPlanService workoutPlanService) {
        this.workoutPlanService = workoutPlanService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkoutPlanResponse create(@AuthenticationPrincipal UserPrincipal me,
                                      @Valid @RequestBody WorkoutPlanRequest request) {
        return workoutPlanService.create(me.getId(), request);
    }

    @GetMapping("/my")
    public List<WorkoutPlanResponse> myPlans(@AuthenticationPrincipal UserPrincipal me) {
        return workoutPlanService.getMyPlans(me.getId(), me.getRole());
    }
}
