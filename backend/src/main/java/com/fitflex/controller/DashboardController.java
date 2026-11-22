package com.fitflex.controller;

import com.fitflex.dto.response.TrainerDashboardResponse;
import com.fitflex.dto.response.UserDashboardResponse;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.DashboardService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Aggregated data for the dashboard pages. /user is USER-only, /trainer is TRAINER-only (SecurityConfig). */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/user")
    public UserDashboardResponse user(@AuthenticationPrincipal UserPrincipal me) {
        return dashboardService.forUser(me.getId());
    }

    @GetMapping("/trainer")
    public TrainerDashboardResponse trainer(@AuthenticationPrincipal UserPrincipal me) {
        return dashboardService.forTrainer(me.getId());
    }
}
