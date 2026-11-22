package com.fitflex.service;

import com.fitflex.dto.request.UserProfileUpdateRequest;
import com.fitflex.dto.response.UserProfileResponse;
import com.fitflex.entity.User;
import com.fitflex.exception.ResourceNotFoundException;
import com.fitflex.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Reading and updating a user's own profile. Also offers a shared "load user or 404" helper. */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Used by other services: load the User entity or fail with 404. */
    @Transactional(readOnly = true)
    public User getUserEntity(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(Long userId) {
        return UserProfileResponse.from(getUserEntity(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(Long userId, UserProfileUpdateRequest request) {
        User user = getUserEntity(userId);
        user.setFullName(request.fullName().trim());
        // Only overwrite optional fields that were actually sent.
        if (request.phone() != null) user.setPhone(request.phone());
        if (request.age() != null) user.setAge(request.age());
        if (request.gender() != null) user.setGender(request.gender());
        if (request.heightCm() != null) user.setHeightCm(request.heightCm());
        if (request.weightKg() != null) user.setWeightKg(request.weightKg());
        if (request.fitnessGoal() != null) user.setFitnessGoal(request.fitnessGoal());
        if (request.fitnessLevel() != null) user.setFitnessLevel(request.fitnessLevel());
        if (request.activityLevel() != null) user.setActivityLevel(request.activityLevel());
        // No save() call needed: inside a transaction JPA writes changes to a loaded entity automatically.
        return UserProfileResponse.from(user);
    }
}
