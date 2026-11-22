package com.fitflex.repository;

import com.fitflex.entity.SessionStatus;
import com.fitflex.entity.SessionType;
import com.fitflex.entity.TrainingSession;
import com.fitflex.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/** Database access for {@link TrainingSession}s (bookings and group classes). */
public interface TrainingSessionRepository extends JpaRepository<TrainingSession, Long> {

    List<TrainingSession> findByTrainerIdOrderByScheduledAtAsc(Long trainerId);

    /** Sessions a user is part of: booked one-on-ones plus group sessions they joined. */
    @Query("""
            SELECT DISTINCT s FROM TrainingSession s LEFT JOIN s.participants p
            WHERE s.user.id = :userId OR p.id = :userId
            ORDER BY s.scheduledAt ASC
            """)
    List<TrainingSession> findAllForUser(@Param("userId") Long userId);

    /** A trainer's sessions with the given statuses in a time range; used to detect booking clashes. */
    List<TrainingSession> findByTrainerIdAndStatusInAndScheduledAtBetween(
            Long trainerId, Collection<SessionStatus> statuses, LocalDateTime from, LocalDateTime to);

    /** Future group sessions with the given statuses (e.g. ones users can still join). */
    List<TrainingSession> findBySessionTypeAndStatusInAndScheduledAtAfterOrderByScheduledAtAsc(
            SessionType sessionType, Collection<SessionStatus> statuses, LocalDateTime after);

    /** Users who booked a one-on-one with the trainer (ignoring sessions with the excluded status). */
    @Query("""
            SELECT DISTINCT s.user FROM TrainingSession s
            WHERE s.trainer.id = :trainerId AND s.user IS NOT NULL AND s.status <> :excluded
            """)
    List<User> findOneOnOneClients(@Param("trainerId") Long trainerId,
                                   @Param("excluded") SessionStatus excluded);

    /** Users who joined one of the trainer's group sessions. */
    @Query("""
            SELECT DISTINCT p FROM TrainingSession s JOIN s.participants p
            WHERE s.trainer.id = :trainerId AND s.status <> :excluded
            """)
    List<User> findGroupClients(@Param("trainerId") Long trainerId,
                                @Param("excluded") SessionStatus excluded);
}
