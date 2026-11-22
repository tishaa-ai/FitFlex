package com.fitflex.controller;

import com.fitflex.dto.request.AvailabilityRequest;
import com.fitflex.dto.request.LoginRequest;
import com.fitflex.dto.request.TrainerProfileUpdateRequest;
import com.fitflex.dto.request.TrainerRegisterRequest;
import com.fitflex.dto.response.AuthResponse;
import com.fitflex.dto.response.AvailabilitySlotResponse;
import com.fitflex.dto.response.TrainerResponse;
import com.fitflex.dto.response.UserProfileResponse;
import com.fitflex.entity.Role;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.AuthService;
import com.fitflex.service.TrainerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Trainer portal endpoints under /api/trainer (singular): registration ("add", the name used in
 * the project report), login, and the logged-in trainer's own profile, availability and clients.
 * SecurityConfig restricts everything except add/login to the TRAINER role.
 */
@RestController
@RequestMapping("/api/trainer")
public class TrainerController {

    private final AuthService authService;
    private final TrainerService trainerService;

    public TrainerController(AuthService authService, TrainerService trainerService) {
        this.authService = authService;
        this.trainerService = trainerService;
    }

    @PostMapping("/add")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody TrainerRegisterRequest request) {
        return authService.registerTrainer(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request, Role.TRAINER);
    }

    @GetMapping("/profile")
    public TrainerResponse getProfile(@AuthenticationPrincipal UserPrincipal me) {
        return trainerService.getMyProfile(me.getId());
    }

    @PutMapping("/profile")
    public TrainerResponse updateProfile(@AuthenticationPrincipal UserPrincipal me,
                                         @Valid @RequestBody TrainerProfileUpdateRequest request) {
        return trainerService.updateMyProfile(me.getId(), request);
    }

    @GetMapping("/availability")
    public List<AvailabilitySlotResponse> getAvailability(@AuthenticationPrincipal UserPrincipal me) {
        return trainerService.getMyAvailability(me.getId());
    }

    @PostMapping("/availability")
    @ResponseStatus(HttpStatus.CREATED)
    public AvailabilitySlotResponse addAvailability(@AuthenticationPrincipal UserPrincipal me,
                                                    @Valid @RequestBody AvailabilityRequest request) {
        return trainerService.addAvailability(me.getId(), request);
    }

    @DeleteMapping("/availability/{slotId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAvailability(@AuthenticationPrincipal UserPrincipal me, @PathVariable Long slotId) {
        trainerService.deleteAvailability(me.getId(), slotId);
    }

    @GetMapping("/clients")
    public List<UserProfileResponse> getClients(@AuthenticationPrincipal UserPrincipal me) {
        return trainerService.getMyClients(me.getId());
    }
}
