package com.fitflex.controller;

import com.fitflex.dto.request.LoginRequest;
import com.fitflex.dto.request.UserProfileUpdateRequest;
import com.fitflex.dto.request.UserRegisterRequest;
import com.fitflex.dto.response.AuthResponse;
import com.fitflex.dto.response.UserProfileResponse;
import com.fitflex.entity.Role;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.AuthService;
import com.fitflex.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * User portal endpoints: register, login and the logged-in user's profile.
 * Controllers only route and validate; all logic is in the services.
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final AuthService authService;
    private final UserService userService;

    public UserController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody UserRegisterRequest request) {
        return authService.registerUser(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request, Role.USER);
    }

    @GetMapping("/profile")
    public UserProfileResponse getProfile(@AuthenticationPrincipal UserPrincipal me) {
        return userService.getProfile(me.getId());
    }

    @PutMapping("/profile")
    public UserProfileResponse updateProfile(@AuthenticationPrincipal UserPrincipal me,
                                             @Valid @RequestBody UserProfileUpdateRequest request) {
        return userService.updateProfile(me.getId(), request);
    }
}
