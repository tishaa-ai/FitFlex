package com.fitflex.dto.response;

import com.fitflex.entity.SessionStatus;
import com.fitflex.entity.SessionType;
import com.fitflex.entity.TrainingSession;
import com.fitflex.entity.User;

import java.time.LocalDateTime;

/**
 * A training session as seen by one particular viewer. The Jitsi {@code meetingLink} is only
 * included when the session is CONFIRMED and the viewer is the trainer, the booking user or a
 * participant, so strangers browsing group sessions cannot join the call.
 */
public record SessionResponse(
        Long id,
        SessionType sessionType,
        String title,
        String description,
        LocalDateTime scheduledAt,
        Integer durationMinutes,
        SessionStatus status,
        String meetingLink,
        Long trainerId,
        String trainerName,
        String trainerSpecialization,
        Long userId,
        String userName,
        String userEmail,
        int participantCount,
        Integer maxParticipants,
        boolean joined) {

    /**
     * @param viewerUserId the User id of whoever asked (trainer's user id or a normal user's id)
     */
    public static SessionResponse from(TrainingSession session, Long viewerUserId) {
        User booker = session.getUser();
        boolean isTrainer = session.getTrainer().getUser().getId().equals(viewerUserId);
        boolean isBooker = booker != null && booker.getId().equals(viewerUserId);
        boolean isParticipant = session.getParticipants().stream().anyMatch(u -> u.getId().equals(viewerUserId));

        boolean canSeeLink = session.getStatus() == SessionStatus.CONFIRMED && (isTrainer || isBooker || isParticipant);

        return new SessionResponse(
                session.getId(), session.getSessionType(), session.getTitle(), session.getDescription(),
                session.getScheduledAt(), session.getDurationMinutes(), session.getStatus(),
                canSeeLink ? session.getMeetingLink() : null,
                session.getTrainer().getId(), session.getTrainer().getUser().getFullName(),
                session.getTrainer().getSpecialization(),
                booker != null ? booker.getId() : null,
                booker != null ? booker.getFullName() : null,
                booker != null && isTrainer ? booker.getEmail() : null,
                session.getParticipants().size(), session.getMaxParticipants(),
                isBooker || isParticipant);
    }
}
