package com.fitflex.service;

import com.fitflex.dto.request.BookSessionRequest;
import com.fitflex.dto.response.SessionResponse;
import com.fitflex.entity.AvailabilitySlot;
import com.fitflex.entity.Role;
import com.fitflex.entity.SessionStatus;
import com.fitflex.entity.SessionType;
import com.fitflex.entity.Trainer;
import com.fitflex.entity.TrainingSession;
import com.fitflex.entity.User;
import com.fitflex.exception.BadRequestException;
import com.fitflex.exception.ConflictException;
import com.fitflex.exception.ForbiddenException;
import com.fitflex.repository.AvailabilitySlotRepository;
import com.fitflex.repository.TrainingSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the booking rules in {@link SessionService}: clash detection, availability,
 * future time, the confirm-creates-Jitsi-link rule and group capacity.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private TrainingSessionRepository sessionRepository;
    @Mock
    private AvailabilitySlotRepository availabilityRepository;
    @Mock
    private TrainerService trainerService;
    @Mock
    private UserService userService;

    @InjectMocks
    private SessionService sessionService;

    private User client;
    private Trainer trainer;
    /** A Monday at least a week away, so "future" is always true. */
    private LocalDate monday;

    @BeforeEach
    void setUp() {
        client = new User();
        client.setId(10L);
        client.setFullName("Rahul Verma");
        client.setRole(Role.USER);

        User trainerUser = new User();
        trainerUser.setId(20L);
        trainerUser.setFullName("Arjun Mehta");
        trainerUser.setRole(Role.TRAINER);
        trainer = new Trainer();
        trainer.setId(1L);
        trainer.setUser(trainerUser);
        trainer.setSpecialization("Strength");

        monday = LocalDate.now().plusDays(7).with(TemporalAdjusters.next(DayOfWeek.MONDAY));

        // Trainer works Mondays 07:00-11:00.
        AvailabilitySlot slot = new AvailabilitySlot();
        slot.setTrainer(trainer);
        slot.setDayOfWeek(DayOfWeek.MONDAY);
        slot.setStartTime(LocalTime.of(7, 0));
        slot.setEndTime(LocalTime.of(11, 0));

        lenient().when(userService.getUserEntity(10L)).thenReturn(client);
        lenient().when(trainerService.getTrainerEntity(1L)).thenReturn(trainer);
        lenient().when(availabilityRepository.findByTrainerIdAndDayOfWeek(eq(1L), any()))
                .thenAnswer(inv -> inv.getArgument(1) == DayOfWeek.MONDAY ? List.of(slot) : List.of());
        lenient().when(availabilityRepository.findByTrainerId(1L)).thenReturn(List.of(slot));
        lenient().when(sessionRepository.save(any(TrainingSession.class))).thenAnswer(inv -> {
            TrainingSession s = inv.getArgument(0);
            s.setId(99L);
            return s;
        });
    }

    private BookSessionRequest booking(LocalDateTime when) {
        return new BookSessionRequest(1L, when, 60, "Leg day", null);
    }

    private TrainingSession existingSession(LocalDateTime start, int minutes, SessionStatus status) {
        TrainingSession s = new TrainingSession();
        s.setId(5L);
        s.setTrainer(trainer);
        s.setUser(client);
        s.setSessionType(SessionType.ONE_ON_ONE);
        s.setTitle("Existing");
        s.setScheduledAt(start);
        s.setDurationMinutes(minutes);
        s.setStatus(status);
        return s;
    }

    @Test
    void validBookingIsSavedAsRequested() {
        when(sessionRepository.findByTrainerIdAndStatusInAndScheduledAtBetween(anyLong(), anyCollection(), any(), any()))
                .thenReturn(List.of());

        SessionResponse response = sessionService.book(10L, booking(monday.atTime(8, 0)));

        assertThat(response.status()).isEqualTo(SessionStatus.REQUESTED);
        assertThat(response.meetingLink()).isNull(); // the link only appears after the trainer confirms
        verify(sessionRepository).save(any(TrainingSession.class));
    }

    @Test
    void bookingThatOverlapsAnotherSessionIsRejected() {
        // Trainer already has 08:00-09:00; the new request is 08:30-09:30.
        when(sessionRepository.findByTrainerIdAndStatusInAndScheduledAtBetween(anyLong(), anyCollection(), any(), any()))
                .thenReturn(List.of(existingSession(monday.atTime(8, 0), 60, SessionStatus.CONFIRMED)));

        assertThatThrownBy(() -> sessionService.book(10L, booking(monday.atTime(8, 30))))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("already has a session");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void bookingRightAfterAnotherSessionIsAllowed() {
        // 08:00-09:00 exists; 09:00-10:00 only touches it, so it does not overlap.
        when(sessionRepository.findByTrainerIdAndStatusInAndScheduledAtBetween(anyLong(), anyCollection(), any(), any()))
                .thenReturn(List.of(existingSession(monday.atTime(8, 0), 60, SessionStatus.CONFIRMED)));

        assertThat(sessionService.book(10L, booking(monday.atTime(9, 0))).status()).isEqualTo(SessionStatus.REQUESTED);
    }

    @Test
    void bookingOutsideAvailabilityIsRejected() {
        // 10:30-11:30 runs past the 11:00 end of the slot.
        assertThatThrownBy(() -> sessionService.book(10L, booking(monday.atTime(10, 30))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("not available")
                .hasMessageContaining("MONDAY 07:00-11:00");
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void bookingOnDayWithoutSlotsIsRejected() {
        LocalDateTime tuesday = monday.plusDays(1).atTime(8, 0);
        assertThatThrownBy(() -> sessionService.book(10L, booking(tuesday)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void bookingInThePastIsRejected() {
        assertThatThrownBy(() -> sessionService.book(10L, booking(LocalDateTime.now().minusDays(1))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("future");
    }

    @Test
    void trainerConfirmingCreatesJitsiLink() {
        TrainingSession requested = existingSession(monday.atTime(8, 0), 60, SessionStatus.REQUESTED);
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(requested));

        SessionResponse response = sessionService.updateStatus(20L, Role.TRAINER, 5L, SessionStatus.CONFIRMED);

        assertThat(response.status()).isEqualTo(SessionStatus.CONFIRMED);
        assertThat(response.meetingLink()).startsWith("https://meet.jit.si/FitFlex-");
    }

    @Test
    void userCannotConfirmSessions() {
        when(sessionRepository.findById(5L))
                .thenReturn(Optional.of(existingSession(monday.atTime(8, 0), 60, SessionStatus.REQUESTED)));

        assertThatThrownBy(() -> sessionService.updateStatus(10L, Role.USER, 5L, SessionStatus.CONFIRMED))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void fullGroupSessionCannotBeJoined() {
        TrainingSession group = existingSession(monday.atTime(18, 0), 45, SessionStatus.CONFIRMED);
        group.setSessionType(SessionType.GROUP);
        group.setUser(null);
        group.setMaxParticipants(1);
        User other = new User();
        other.setId(11L);
        group.getParticipants().add(other);
        when(sessionRepository.findById(5L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> sessionService.join(10L, 5L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("full");
    }
}
