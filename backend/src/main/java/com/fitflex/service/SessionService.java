package com.fitflex.service;

import com.fitflex.dto.request.BookSessionRequest;
import com.fitflex.dto.request.GroupSessionRequest;
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
import com.fitflex.exception.ResourceNotFoundException;
import com.fitflex.repository.AvailabilitySlotRepository;
import com.fitflex.repository.TrainingSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Booking one-on-one sessions, creating and joining group sessions, and moving sessions
 * through their statuses. The booking rules (future time, inside availability, no clash)
 * live here and are unit tested in SessionServiceTest.
 */
@Service
public class SessionService {

    /** Statuses that block the trainer's calendar. Cancelled and completed sessions do not. */
    private static final Set<SessionStatus> ACTIVE = EnumSet.of(SessionStatus.REQUESTED, SessionStatus.CONFIRMED);
    private static final int DEFAULT_DURATION_MINUTES = 60;
    private static final String JITSI_BASE_URL = "https://meet.jit.si/FitFlex-";

    private final TrainingSessionRepository sessionRepository;
    private final AvailabilitySlotRepository availabilityRepository;
    private final TrainerService trainerService;
    private final UserService userService;

    public SessionService(TrainingSessionRepository sessionRepository, AvailabilitySlotRepository availabilityRepository,
                          TrainerService trainerService, UserService userService) {
        this.sessionRepository = sessionRepository;
        this.availabilityRepository = availabilityRepository;
        this.trainerService = trainerService;
        this.userService = userService;
    }

    // ===================== creating sessions =====================

    /** A USER requests a one-on-one. It starts as REQUESTED until the trainer confirms. */
    @Transactional
    public SessionResponse book(Long userId, BookSessionRequest request) {
        User user = userService.getUserEntity(userId);
        Trainer trainer = trainerService.getTrainerEntity(request.trainerId());

        LocalDateTime start = request.scheduledAt().truncatedTo(ChronoUnit.MINUTES);
        int duration = request.durationMinutes() != null ? request.durationMinutes() : DEFAULT_DURATION_MINUTES;
        LocalDateTime end = start.plusMinutes(duration);

        // Rule 1: must be in the future (the @Future annotation checks this too, but services
        // should not trust that every caller went through the controller).
        if (!start.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Session time must be in the future");
        }
        if (!end.toLocalDate().equals(start.toLocalDate())) {
            throw new BadRequestException("A session must start and end on the same day");
        }
        // Rule 2: must fit completely inside one of the trainer's weekly slots.
        ensureInsideAvailability(trainer, start, end);
        // Rule 3: must not overlap another active session of this trainer.
        ensureNoClash(trainer.getId(), start, end);

        TrainingSession session = new TrainingSession();
        session.setTrainer(trainer);
        session.setUser(user);
        session.setSessionType(SessionType.ONE_ON_ONE);
        session.setTitle(isBlank(request.title())
                ? "One-on-one with " + trainer.getUser().getFullName()
                : request.title().trim());
        session.setDescription(request.description());
        session.setScheduledAt(start);
        session.setDurationMinutes(duration);
        session.setStatus(SessionStatus.REQUESTED);
        return SessionResponse.from(sessionRepository.save(session), userId);
    }

    /** A TRAINER creates a group class. It is CONFIRMED immediately and gets its video link. */
    @Transactional
    public SessionResponse createGroup(Long trainerUserId, GroupSessionRequest request) {
        Trainer trainer = trainerService.getTrainerByUserId(trainerUserId);
        LocalDateTime start = request.scheduledAt().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime end = start.plusMinutes(request.durationMinutes());

        if (!start.isAfter(LocalDateTime.now())) {
            throw new BadRequestException("Session time must be in the future");
        }
        ensureNoClash(trainer.getId(), start, end);

        TrainingSession session = new TrainingSession();
        session.setTrainer(trainer);
        session.setSessionType(SessionType.GROUP);
        session.setTitle(request.title().trim());
        session.setDescription(request.description());
        session.setScheduledAt(start);
        session.setDurationMinutes(request.durationMinutes());
        session.setMaxParticipants(request.maxParticipants());
        session.setStatus(SessionStatus.CONFIRMED);
        session.setMeetingLink(newMeetingLink());
        return SessionResponse.from(sessionRepository.save(session), trainerUserId);
    }

    /** A USER joins a group session if it is upcoming, confirmed and not full. */
    @Transactional
    public SessionResponse join(Long userId, Long sessionId) {
        TrainingSession session = getSessionEntity(sessionId);
        User user = userService.getUserEntity(userId);

        if (session.getSessionType() != SessionType.GROUP) {
            throw new BadRequestException("Only group sessions can be joined. Book one-on-one sessions instead.");
        }
        if (session.getStatus() != SessionStatus.CONFIRMED || !session.getScheduledAt().isAfter(LocalDateTime.now())) {
            throw new BadRequestException("This group session is no longer open for joining");
        }
        boolean alreadyJoined = session.getParticipants().stream().anyMatch(u -> u.getId().equals(userId));
        if (alreadyJoined) {
            throw new ConflictException("You have already joined this session");
        }
        if (session.getMaxParticipants() != null && session.getParticipants().size() >= session.getMaxParticipants()) {
            throw new ConflictException("Sorry, this group session is full");
        }
        session.getParticipants().add(user);
        return SessionResponse.from(session, userId);
    }

    // ===================== reading sessions =====================

    /**
     * Sessions of the logged-in user or trainer.
     *
     * @param filter "upcoming" (not finished and still active), "past" (everything else) or null for all
     */
    @Transactional(readOnly = true)
    public List<SessionResponse> getMySessions(Long userId, Role role, String filter) {
        List<TrainingSession> sessions = role == Role.TRAINER
                ? sessionRepository.findByTrainerIdOrderByScheduledAtAsc(trainerService.getTrainerByUserId(userId).getId())
                : sessionRepository.findAllForUser(userId);

        LocalDateTime now = LocalDateTime.now();
        if ("upcoming".equalsIgnoreCase(filter)) {
            sessions = sessions.stream().filter(s -> isUpcoming(s, now)).toList();
        } else if ("past".equalsIgnoreCase(filter)) {
            sessions = sessions.stream()
                    .filter(s -> !isUpcoming(s, now))
                    .sorted(Comparator.comparing(TrainingSession::getScheduledAt).reversed()) // newest first
                    .toList();
        }
        return sessions.stream().map(s -> SessionResponse.from(s, userId)).toList();
    }

    /** Upcoming group sessions anyone can browse (the "joined" flag tells the user if they are in). */
    @Transactional(readOnly = true)
    public List<SessionResponse> getOpenGroupSessions(Long viewerUserId) {
        return sessionRepository.findBySessionTypeAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
                        SessionType.GROUP, EnumSet.of(SessionStatus.CONFIRMED), LocalDateTime.now())
                .stream()
                .map(s -> SessionResponse.from(s, viewerUserId))
                .toList();
    }

    // ===================== changing status =====================

    /**
     * Allowed changes:
     * TRAINER (own sessions): REQUESTED -> CONFIRMED (creates the Jitsi link),
     * REQUESTED/CONFIRMED -> CANCELLED, CONFIRMED -> COMPLETED (only once it has started).
     * USER: may only CANCEL. For their own one-on-one it cancels the booking; for a group
     * session it removes them from the participant list (leaves the class).
     */
    @Transactional
    public SessionResponse updateStatus(Long userId, Role role, Long sessionId, SessionStatus newStatus) {
        TrainingSession session = getSessionEntity(sessionId);
        if (role == Role.TRAINER) {
            updateAsTrainer(userId, session, newStatus);
        } else {
            updateAsUser(userId, session, newStatus);
        }
        return SessionResponse.from(session, userId);
    }

    private void updateAsTrainer(Long trainerUserId, TrainingSession session, SessionStatus newStatus) {
        if (!session.getTrainer().getUser().getId().equals(trainerUserId)) {
            throw new ForbiddenException("This session belongs to another trainer");
        }
        SessionStatus current = session.getStatus();
        switch (newStatus) {
            case CONFIRMED -> {
                if (current != SessionStatus.REQUESTED) {
                    throw new BadRequestException("Only REQUESTED sessions can be confirmed (current: " + current + ")");
                }
                session.setMeetingLink(newMeetingLink());
            }
            case CANCELLED -> {
                if (!ACTIVE.contains(current)) {
                    throw new BadRequestException("Only requested or confirmed sessions can be cancelled");
                }
            }
            case COMPLETED -> {
                if (current != SessionStatus.CONFIRMED) {
                    throw new BadRequestException("Only CONFIRMED sessions can be marked completed");
                }
                if (session.getScheduledAt().isAfter(LocalDateTime.now())) {
                    throw new BadRequestException("A session can be marked completed only after it has started");
                }
            }
            default -> throw new BadRequestException("Status cannot be changed to " + newStatus);
        }
        session.setStatus(newStatus);
    }

    private void updateAsUser(Long userId, TrainingSession session, SessionStatus newStatus) {
        if (newStatus != SessionStatus.CANCELLED) {
            throw new ForbiddenException("Users can only cancel sessions");
        }
        if (!ACTIVE.contains(session.getStatus())) {
            throw new BadRequestException("This session can no longer be cancelled");
        }

        if (session.getSessionType() == SessionType.ONE_ON_ONE) {
            if (session.getUser() == null || !session.getUser().getId().equals(userId)) {
                throw new ForbiddenException("You can only cancel your own bookings");
            }
            session.setStatus(SessionStatus.CANCELLED);
        } else {
            boolean removed = session.getParticipants().removeIf(u -> u.getId().equals(userId));
            if (!removed) {
                throw new ForbiddenException("You have not joined this group session");
            }
        }
    }

    // ===================== rules and helpers =====================

    /** Throws 400 unless [start, end] lies completely inside one availability slot on that weekday. */
    private void ensureInsideAvailability(Trainer trainer, LocalDateTime start, LocalDateTime end) {
        List<AvailabilitySlot> slots = availabilityRepository.findByTrainerIdAndDayOfWeek(trainer.getId(), start.getDayOfWeek());
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        boolean fits = slots.stream().anyMatch(slot ->
                !startTime.isBefore(slot.getStartTime()) && !endTime.isAfter(slot.getEndTime()));

        if (!fits) {
            List<AvailabilitySlot> all = availabilityRepository.findByTrainerId(trainer.getId());
            String hours = all.isEmpty() ? "no slots yet"
                    : all.stream()
                    .sorted(AvailabilitySlot.WEEK_ORDER)
                    .map(s -> s.getDayOfWeek() + " " + s.getStartTime() + "-" + s.getEndTime())
                    .collect(Collectors.joining(", "));
            throw new BadRequestException("The trainer is not available at that time. Available: " + hours);
        }
    }

    /** Throws 409 if any active session of the trainer overlaps [start, end). */
    private void ensureNoClash(Long trainerId, LocalDateTime start, LocalDateTime end) {
        // Look back 4 hours too: a session that started earlier may still be running at `start`
        // (the longest session we allow is 3 hours).
        List<TrainingSession> nearby = sessionRepository.findByTrainerIdAndStatusInAndScheduledAtBetween(
                trainerId, ACTIVE, start.minusHours(4), end);

        for (TrainingSession other : nearby) {
            LocalDateTime otherStart = other.getScheduledAt();
            LocalDateTime otherEnd = otherStart.plusMinutes(other.getDurationMinutes());
            // Same overlap test as for availability: each starts before the other ends.
            if (otherStart.isBefore(end) && start.isBefore(otherEnd)) {
                throw new ConflictException("The trainer already has a session from " + otherStart.toLocalTime()
                        + " to " + otherEnd.toLocalTime() + " on " + otherStart.toLocalDate() + ". Please pick another time.");
            }
        }
    }

    private boolean isUpcoming(TrainingSession session, LocalDateTime now) {
        LocalDateTime end = session.getScheduledAt().plusMinutes(session.getDurationMinutes());
        return ACTIVE.contains(session.getStatus()) && end.isAfter(now);
    }

    private TrainingSession getSessionEntity(Long sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Session not found"));
    }

    /** Jitsi rooms are created on the fly by URL, so a random UUID gives each session a private room. */
    private String newMeetingLink() {
        return JITSI_BASE_URL + UUID.randomUUID();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
