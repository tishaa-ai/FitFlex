package com.fitflex.service;

import com.fitflex.dto.request.LoginRequest;
import com.fitflex.dto.request.UserRegisterRequest;
import com.fitflex.dto.response.AuthResponse;
import com.fitflex.entity.ActivityLevel;
import com.fitflex.entity.FitnessGoal;
import com.fitflex.entity.FitnessLevel;
import com.fitflex.entity.Gender;
import com.fitflex.entity.Role;
import com.fitflex.entity.User;
import com.fitflex.exception.ConflictException;
import com.fitflex.exception.UnauthorizedException;
import com.fitflex.repository.TrainerRepository;
import com.fitflex.repository.UserRepository;
import com.fitflex.security.JwtUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}. The repositories, password encoder and JWT utility are
 * Mockito mocks, so no database or Spring context is needed: these tests run in milliseconds.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private TrainerRepository trainerRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    private UserRegisterRequest registerRequest(String email) {
        return new UserRegisterRequest("Asha Rao", email, "Passw0rd!", "9876500000", 26, Gender.FEMALE,
                160.0, 58.0, FitnessGoal.MAINTENANCE, FitnessLevel.BEGINNER, ActivityLevel.LIGHT);
    }

    private User existingUser(Role role) {
        User user = new User();
        user.setId(7L);
        user.setFullName("Asha Rao");
        user.setEmail("asha@example.com");
        user.setPassword("$2a$10$hashed");
        user.setRole(role);
        return user;
    }

    @Test
    void registerRejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("asha@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registerUser(registerRequest("Asha@Example.com")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already exists");

        verify(userRepository, never()).save(any());
    }

    @Test
    void registerStoresHashedPasswordAndReturnsToken() {
        when(userRepository.existsByEmailIgnoreCase("asha@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Passw0rd!")).thenReturn("$2a$10$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            saved.setId(7L);
            return saved;
        });
        when(jwtUtil.generateToken(any(User.class))).thenReturn("jwt-token");

        AuthResponse response = authService.registerUser(registerRequest("  Asha@Example.com "));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("$2a$10$hashed");      // never the plain password
        assertThat(saved.getEmail()).isEqualTo("asha@example.com");      // trimmed + lower-cased
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.id()).isEqualTo(7L);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = existingUser(Role.USER);
        when(userRepository.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPass1", user.getPassword())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "WrongPass1"), Role.USER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");

        verify(jwtUtil, never()).generateToken(any());
    }

    @Test
    void loginRejectsUnknownEmailWithSameMessage() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@example.com", "Passw0rd!"), Role.USER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void trainerCannotLogInThroughUserPortal() {
        User trainer = existingUser(Role.TRAINER);
        when(userRepository.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(trainer));
        when(passwordEncoder.matches("Passw0rd!", trainer.getPassword())).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("asha@example.com", "Passw0rd!"), Role.USER))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessageContaining("trainer login");
    }

    @Test
    void loginWithCorrectPasswordReturnsToken() {
        User user = existingUser(Role.USER);
        when(userRepository.findByEmailIgnoreCase("asha@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Passw0rd!", user.getPassword())).thenReturn(true);
        when(jwtUtil.generateToken(user)).thenReturn("jwt-token");

        AuthResponse response = authService.login(new LoginRequest("asha@example.com", "Passw0rd!"), Role.USER);

        assertThat(response.token()).isEqualTo("jwt-token");
        assertThat(response.role()).isEqualTo(Role.USER);
        assertThat(response.trainerId()).isNull();
    }
}
