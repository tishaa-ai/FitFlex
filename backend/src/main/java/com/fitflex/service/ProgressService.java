package com.fitflex.service;

import com.fitflex.dto.request.PoseSessionRequest;
import com.fitflex.dto.request.ProgressRequest;
import com.fitflex.dto.response.ProgressResponse;
import com.fitflex.dto.response.ProgressSummaryResponse;
import com.fitflex.entity.ProgressEntry;
import com.fitflex.entity.ProgressSource;
import com.fitflex.entity.User;
import com.fitflex.exception.BadRequestException;
import com.fitflex.repository.ProgressEntryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Progress tracking: manual logs, sessions uploaded by the pose detection script, the
 * filtered history for charts, and the summary numbers (streak, totals, weight change, form accuracy).
 */
@Service
public class ProgressService {

    /** Rough average for moderate exercise, used when the pose script does not send calories. */
    private static final int ESTIMATED_KCAL_PER_MINUTE = 6;

    private final ProgressEntryRepository progressRepository;
    private final UserService userService;

    public ProgressService(ProgressEntryRepository progressRepository, UserService userService) {
        this.progressRepository = progressRepository;
        this.userService = userService;
    }

    @Transactional
    public ProgressResponse logManual(Long userId, ProgressRequest request) {
        if (request.weightKg() == null && request.workoutMinutes() == null) {
            throw new BadRequestException("Enter your weight, your workout minutes, or both");
        }
        User user = userService.getUserEntity(userId);
        LocalDate date = request.date() != null ? request.date() : LocalDate.now();

        ProgressEntry entry = new ProgressEntry();
        entry.setUser(user);
        entry.setDate(date);
        entry.setWeightKg(request.weightKg());
        entry.setWorkoutMinutes(request.workoutMinutes());
        entry.setCaloriesBurned(request.caloriesBurned());
        entry.setNotes(request.notes());
        entry.setSource(ProgressSource.MANUAL);

        // Keep the profile weight current, so the next diet plan uses today's weight.
        if (request.weightKg() != null && !date.isBefore(LocalDate.now())) {
            user.setWeightKg(request.weightKg());
        }
        return ProgressResponse.from(progressRepository.save(entry));
    }

    /** Called by the Python pose script after a workout. */
    @Transactional
    public ProgressResponse logPoseSession(Long userId, PoseSessionRequest request) {
        User user = userService.getUserEntity(userId);
        int minutes = request.durationSeconds() > 0 ? Math.max(1, Math.round(request.durationSeconds() / 60f)) : 0;

        ProgressEntry entry = new ProgressEntry();
        entry.setUser(user);
        entry.setDate(LocalDate.now());
        entry.setSource(ProgressSource.POSE_DETECTION);
        entry.setExercise(request.exercise().trim().toUpperCase(Locale.ROOT));
        entry.setReps(request.reps());
        entry.setCorrectFormPercent(round1(request.correctFormPercent()));
        entry.setWorkoutMinutes(minutes);
        entry.setCaloriesBurned(request.caloriesBurned() != null ? request.caloriesBurned() : minutes * ESTIMATED_KCAL_PER_MINUTE);
        entry.setNotes(request.notes());
        return ProgressResponse.from(progressRepository.save(entry));
    }

    /** History for charts and tables, oldest first. {@code from}/{@code to} are optional. */
    @Transactional(readOnly = true)
    public List<ProgressResponse> getHistory(Long userId, LocalDate from, LocalDate to) {
        List<ProgressEntry> entries = (from == null && to == null)
                ? progressRepository.findByUserIdOrderByDateAscIdAsc(userId)
                : progressRepository.findByUserIdAndDateBetweenOrderByDateAscIdAsc(userId,
                from != null ? from : LocalDate.of(2000, 1, 1),
                to != null ? to : LocalDate.now().plusDays(1));
        return entries.stream().map(ProgressResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ProgressSummaryResponse getSummary(Long userId) {
        List<ProgressEntry> entries = progressRepository.findByUserIdOrderByDateAscIdAsc(userId);
        LocalDate today = LocalDate.now();

        // ----- streak: count back day by day while there is a log on that day -----
        Set<LocalDate> loggedDays = entries.stream().map(ProgressEntry::getDate).collect(Collectors.toSet());
        LocalDate day = loggedDays.contains(today) ? today : today.minusDays(1); // today not logged yet still keeps the streak
        int streak = 0;
        while (loggedDays.contains(day)) {
            streak++;
            day = day.minusDays(1);
        }

        // ----- workouts -----
        Set<LocalDate> workoutDays = entries.stream()
                .filter(e -> e.getWorkoutMinutes() != null && e.getWorkoutMinutes() > 0)
                .map(ProgressEntry::getDate)
                .collect(Collectors.toSet());
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        int workoutsThisWeek = (int) workoutDays.stream().filter(d -> !d.isBefore(monday)).count();
        int totalMinutes = entries.stream().mapToInt(e -> nz(e.getWorkoutMinutes())).sum();
        int totalCalories = entries.stream().mapToInt(e -> nz(e.getCaloriesBurned())).sum();

        // ----- weight -----
        List<Double> weights = entries.stream().map(ProgressEntry::getWeightKg).filter(w -> w != null).toList();
        Double startWeight = weights.isEmpty() ? null : weights.get(0);
        Double currentWeight = weights.isEmpty() ? userService.getUserEntity(userId).getWeightKg() : weights.get(weights.size() - 1);
        Double weightChange = weights.size() >= 2 ? round1(currentWeight - startWeight) : null;

        // ----- pose detection -----
        List<ProgressEntry> pose = entries.stream().filter(e -> e.getSource() == ProgressSource.POSE_DETECTION).toList();
        int poseReps = pose.stream().mapToInt(e -> nz(e.getReps())).sum();
        OptionalDouble avgForm = pose.stream()
                .filter(e -> e.getCorrectFormPercent() != null)
                .mapToDouble(ProgressEntry::getCorrectFormPercent)
                .average();

        return new ProgressSummaryResponse(streak, workoutDays.size(), workoutsThisWeek, totalMinutes, totalCalories,
                startWeight, currentWeight, weightChange, pose.size(), poseReps,
                avgForm.isPresent() ? round1(avgForm.getAsDouble()) : null);
    }

    private static int nz(Integer value) {
        return value == null ? 0 : value;
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
