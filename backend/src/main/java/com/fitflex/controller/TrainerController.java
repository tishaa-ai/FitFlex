package com.fitflex.controller;

import com.fitflex.dto.request.LoginRequest;
import com.fitflex.dto.request.TrainerRegisterRequest;
import com.fitflex.dto.response.AuthResponse;
import com.fitflex.entity.Role;
import com.fitflex.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trainer portal endpoints under /api/trainer (singular): registration ("add", the name used in
 * the project report), login, and the logged-in trainer's own data.
 */
@RestController
@RequestMapping("/api/trainer")
public class TrainerController {

    private final AuthService authService;

    public TrainerController(AuthService authService) {
        this.authService = authService;
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
}
