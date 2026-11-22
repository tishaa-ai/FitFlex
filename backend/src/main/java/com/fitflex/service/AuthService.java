package com.fitflex.service;

import com.fitflex.dto.request.LoginRequest;
import com.fitflex.dto.request.TrainerRegisterRequest;
import com.fitflex.dto.request.UserRegisterRequest;
import com.fitflex.dto.response.AuthResponse;
import com.fitflex.entity.Role;
import com.fitflex.entity.Trainer;
import com.fitflex.entity.User;
import com.fitflex.exception.ConflictException;
import com.fitflex.exception.UnauthorizedException;
import com.fitflex.repository.TrainerRepository;
import com.fitflex.repository.UserRepository;
import com.fitflex.security.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/**
 * Registration and login for both portals. Passwords are stored only as BCrypt hashes;
 * a successful register/login returns a JWT for the frontend to keep.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final TrainerRepository trainerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, TrainerRepository trainerRepository,
                       PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.trainerRepository = trainerRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Transactional
    public AuthResponse registerUser(UserRegisterRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailIsFree(email);

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.USER);
        user.setPhone(request.phone());
        user.setAge(request.age());
        user.setGender(request.gender());
        user.setHeightCm(request.heightCm());
        user.setWeightKg(request.weightKg());
        user.setFitnessGoal(request.fitnessGoal());
        user.setFitnessLevel(request.fitnessLevel());
        user.setActivityLevel(request.activityLevel());
        User saved = userRepository.save(user);

        return toAuthResponse(saved, null);
    }

    @Transactional
    public AuthResponse registerTrainer(TrainerRegisterRequest request) {
        String email = normalizeEmail(request.email());
        ensureEmailIsFree(email);

        User user = new User();
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setRole(Role.TRAINER);
        user.setPhone(request.phone());
        User savedUser = userRepository.save(user);

        Trainer trainer = new Trainer();
        trainer.setUser(savedUser);
        trainer.setSpecialization(request.specialization().trim());
        trainer.setExperienceYears(request.experienceYears());
        trainer.setCertifications(request.certifications());
        trainer.setBio(request.bio());
        trainer.setPricePerSession(request.pricePerSession());
        trainer.setProfileImageUrl(request.profileImageUrl());
        trainer.setRating(null);       // no reviews yet: the frontend shows "New"
        trainer.setVerified(false);    // an admin would verify certificates in a real system
        Trainer savedTrainer = trainerRepository.save(trainer);

        return toAuthResponse(savedUser, savedTrainer.getId());
    }

    /**
     * Checks email + password and that the account belongs to the portal being used
     * (a trainer cannot log in through the user portal and vice versa).
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, Role expectedRole) {
        User user = userRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        // Why the same message for "no such email" and "wrong password": it does not reveal
        // to an attacker which emails are registered.
        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        if (user.getRole() != expectedRole) {
            String portal = user.getRole() == Role.TRAINER ? "trainer" : "user";
            throw new UnauthorizedException("This is a " + portal + " account. Please use the " + portal + " login page.");
        }

        Long trainerId = null;
        if (user.getRole() == Role.TRAINER) {
            trainerId = trainerRepository.findByUserId(user.getId()).map(Trainer::getId).orElse(null);
        }
        return toAuthResponse(user, trainerId);
    }

    private void ensureEmailIsFree(String email) {
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("An account with this email already exists");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private AuthResponse toAuthResponse(User user, Long trainerId) {
        String token = jwtUtil.generateToken(user);
        return new AuthResponse(token, user.getRole(), user.getFullName(), user.getId(), trainerId, user.getEmail());
    }
}
