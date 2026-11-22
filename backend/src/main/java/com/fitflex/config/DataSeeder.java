package com.fitflex.config;

import com.fitflex.entity.*;
import com.fitflex.repository.ProgressEntryRepository;
import com.fitflex.repository.TrainerRepository;
import com.fitflex.repository.TrainingSessionRepository;
import com.fitflex.repository.UserRepository;
import com.fitflex.repository.WorkoutPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Fills an EMPTY database with demo data on startup: one demo user, five trainers with
 * availability, a few sessions, a workout plan and four weeks of progress so the dashboards
 * and charts have something to show. Does nothing if any user already exists.
 * Turn it off with SEED_DEMO_DATA=false.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String DEMO_USER_EMAIL = "user@fitflex.com";
    public static final String DEMO_USER_PASSWORD = "User@1234";
    public static final String DEMO_TRAINER_PASSWORD = "Trainer@1234";

    private final UserRepository userRepository;
    private final TrainerRepository trainerRepository;
    private final TrainingSessionRepository sessionRepository;
    private final WorkoutPlanRepository workoutPlanRepository;
    private final ProgressEntryRepository progressRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;

    public DataSeeder(UserRepository userRepository, TrainerRepository trainerRepository,
                      TrainingSessionRepository sessionRepository, WorkoutPlanRepository workoutPlanRepository,
                      ProgressEntryRepository progressRepository, PasswordEncoder passwordEncoder,
                      @Value("${fitflex.seed.enabled:true}") boolean enabled) {
        this.userRepository = userRepository;
        this.trainerRepository = trainerRepository;
        this.sessionRepository = sessionRepository;
        this.workoutPlanRepository = workoutPlanRepository;
        this.progressRepository = progressRepository;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled || userRepository.count() > 0) {
            return;
        }
        log.info("Empty database detected: seeding demo data");

        User demoUser = createDemoUser();
        List<Trainer> trainers = createTrainers();
        createSessions(demoUser, trainers);
        createWorkoutPlan(demoUser, trainers.get(0));
        createProgress(demoUser);

        log.info("Demo data ready. User: {} / {}  Trainers: trainer1..5@fitflex.com / {}",
                DEMO_USER_EMAIL, DEMO_USER_PASSWORD, DEMO_TRAINER_PASSWORD);
    }

    private User createDemoUser() {
        User user = new User();
        user.setFullName("Rahul Verma");
        user.setEmail(DEMO_USER_EMAIL);
        user.setPassword(passwordEncoder.encode(DEMO_USER_PASSWORD));
        user.setRole(Role.USER);
        user.setPhone("9876543210");
        user.setAge(24);
        user.setGender(Gender.MALE);
        user.setHeightCm(175.0);
        user.setWeightKg(75.5);
        user.setFitnessGoal(FitnessGoal.WEIGHT_LOSS);
        user.setFitnessLevel(FitnessLevel.BEGINNER);
        user.setActivityLevel(ActivityLevel.MODERATE);
        return userRepository.save(user);
    }

    private List<Trainer> createTrainers() {
        List<Trainer> trainers = new ArrayList<>();
        trainers.add(trainer(1, "Arjun Mehta", "Strength", 8, "799", 4.8,
                "ACE Certified Personal Trainer, NSCA-CSCS",
                "Former state-level powerlifter. I help beginners build strength safely with progressive overload and perfect form.",
                new Object[][]{{DayOfWeek.MONDAY, "07:00", "11:00"}, {DayOfWeek.WEDNESDAY, "07:00", "11:00"},
                        {DayOfWeek.FRIDAY, "17:00", "21:00"}, {DayOfWeek.SATURDAY, "08:00", "12:00"}}));
        trainers.add(trainer(2, "Priya Sharma", "Yoga", 6, "599", 4.9,
                "RYT-500 Yoga Alliance, Diploma in Yoga Therapy",
                "Hatha and Vinyasa yoga teacher. My classes focus on flexibility, breathing and stress relief for busy professionals.",
                new Object[][]{{DayOfWeek.TUESDAY, "06:00", "09:00"}, {DayOfWeek.THURSDAY, "06:00", "09:00"},
                        {DayOfWeek.SATURDAY, "07:00", "10:00"}, {DayOfWeek.SUNDAY, "07:00", "10:00"}}));
        trainers.add(trainer(3, "Rohan Deshmukh", "Weight Loss", 5, "699", 4.6,
                "ISSA Certified Fitness Trainer, Precision Nutrition L1",
                "Lost 25 kg myself. I combine HIIT, walking targets and simple Indian diet changes for steady fat loss.",
                new Object[][]{{DayOfWeek.MONDAY, "18:00", "21:00"}, {DayOfWeek.TUESDAY, "18:00", "21:00"},
                        {DayOfWeek.THURSDAY, "18:00", "21:00"}, {DayOfWeek.SATURDAY, "16:00", "20:00"}}));
        trainers.add(trainer(4, "Kavya Iyer", "Zumba", 4, "499", 4.7,
                "Licensed Zumba Instructor (ZIN), Aerobics Certification",
                "High-energy dance workouts set to Bollywood and Latin music. Burn calories while having fun!",
                new Object[][]{{DayOfWeek.MONDAY, "07:00", "09:00"}, {DayOfWeek.WEDNESDAY, "17:00", "20:00"},
                        {DayOfWeek.FRIDAY, "07:00", "09:00"}, {DayOfWeek.SUNDAY, "17:00", "20:00"}}));
        trainers.add(trainer(5, "Vikram Singh", "CrossFit", 10, "999", 4.5,
                "CrossFit Level 2 Trainer, Kettlebell Specialist",
                "Ex-Army fitness instructor. Functional training, conditioning and mobility for people who like a challenge.",
                new Object[][]{{DayOfWeek.TUESDAY, "17:00", "21:00"}, {DayOfWeek.WEDNESDAY, "06:00", "09:00"},
                        {DayOfWeek.FRIDAY, "06:00", "09:00"}, {DayOfWeek.SATURDAY, "17:00", "20:00"}}));
        return trainers;
    }

    /** Creates one trainer account + profile + weekly slots. Each slot row is {day, "HH:mm", "HH:mm"}. */
    private Trainer trainer(int number, String name, String specialization, int experience, String price,
                            double rating, String certifications, String bio, Object[][] slots) {
        User user = new User();
        user.setFullName(name);
        user.setEmail("trainer" + number + "@fitflex.com");
        user.setPassword(passwordEncoder.encode(DEMO_TRAINER_PASSWORD));
        user.setRole(Role.TRAINER);
        user.setPhone("98200000" + String.format("%02d", number));
        userRepository.save(user);

        Trainer trainer = new Trainer();
        trainer.setUser(user);
        trainer.setSpecialization(specialization);
        trainer.setExperienceYears(experience);
        trainer.setPricePerSession(new BigDecimal(price));
        trainer.setRating(rating);
        trainer.setCertifications(certifications);
        trainer.setBio(bio);
        trainer.setVerified(true);
        for (Object[] slot : slots) {
            AvailabilitySlot availability = new AvailabilitySlot();
            availability.setTrainer(trainer);
            availability.setDayOfWeek((DayOfWeek) slot[0]);
            availability.setStartTime(LocalTime.parse((String) slot[1]));
            availability.setEndTime(LocalTime.parse((String) slot[2]));
            trainer.getAvailability().add(availability);
        }
        return trainerRepository.save(trainer); // cascade also saves the slots
    }

    private void createSessions(User demoUser, List<Trainer> trainers) {
        Trainer arjun = trainers.get(0);
        Trainer priya = trainers.get(1);
        Trainer rohan = trainers.get(2);
        Trainer kavya = trainers.get(3);

        // Upcoming CONFIRMED one-on-one with Arjun (next Wednesday 08:00, inside his slot).
        TrainingSession confirmed = oneOnOne(arjun, demoUser, "Strength basics: squat & deadlift form",
                next(DayOfWeek.WEDNESDAY, "08:00"), SessionStatus.CONFIRMED);
        confirmed.setMeetingLink("https://meet.jit.si/FitFlex-" + UUID.randomUUID());
        sessionRepository.save(confirmed);

        // Upcoming REQUESTED one-on-one with Rohan, waiting for him to confirm.
        sessionRepository.save(oneOnOne(rohan, demoUser, "Weight loss consultation",
                next(DayOfWeek.THURSDAY, "19:00"), SessionStatus.REQUESTED));

        // Past COMPLETED session with Arjun.
        sessionRepository.save(oneOnOne(arjun, demoUser, "First assessment and goal setting",
                previous(DayOfWeek.MONDAY, "08:00"), SessionStatus.COMPLETED));

        // Group sessions: Priya's yoga (demo user already joined) and Kavya's Zumba (open to join).
        TrainingSession yoga = group(priya, "Morning Yoga Flow", "Gentle vinyasa flow for all levels. Keep a mat and water ready.",
                next(DayOfWeek.SATURDAY, "07:30"), 15);
        yoga.getParticipants().add(demoUser);
        sessionRepository.save(yoga);
        sessionRepository.save(group(kavya, "Bollywood Zumba Party", "45 minutes of fun cardio dance. No experience needed.",
                next(DayOfWeek.SUNDAY, "18:00"), 20));
    }

    private TrainingSession oneOnOne(Trainer trainer, User user, String title, LocalDateTime when, SessionStatus status) {
        TrainingSession session = new TrainingSession();
        session.setTrainer(trainer);
        session.setUser(user);
        session.setSessionType(SessionType.ONE_ON_ONE);
        session.setTitle(title);
        session.setScheduledAt(when);
        session.setDurationMinutes(60);
        session.setStatus(status);
        return session;
    }

    private TrainingSession group(Trainer trainer, String title, String description, LocalDateTime when, int max) {
        TrainingSession session = new TrainingSession();
        session.setTrainer(trainer);
        session.setSessionType(SessionType.GROUP);
        session.setTitle(title);
        session.setDescription(description);
        session.setScheduledAt(when);
        session.setDurationMinutes(45);
        session.setStatus(SessionStatus.CONFIRMED);   // group sessions are confirmed when created
        session.setMeetingLink("https://meet.jit.si/FitFlex-" + UUID.randomUUID());
        session.setMaxParticipants(max);
        return session;
    }

    private void createWorkoutPlan(User demoUser, Trainer trainer) {
        WorkoutPlan plan = new WorkoutPlan();
        plan.setTrainer(trainer);
        plan.setUser(demoUser);
        plan.setTitle("Beginner Full Body - 4 Weeks");
        plan.setGoal("Fat loss and basic strength");
        plan.setDurationWeeks(4);
        plan.addExercise(exercise(1, "Bodyweight Squats", 3, 15, 60, "Keep chest up, knees over toes"));
        plan.addExercise(exercise(1, "Push-ups (knees if needed)", 3, 10, 60, null));
        plan.addExercise(exercise(1, "Plank", 3, 1, 45, "Hold 30 seconds"));
        plan.addExercise(exercise(2, "Dumbbell Bicep Curls", 3, 12, 60, "Elbows close to the body"));
        plan.addExercise(exercise(2, "Lunges", 3, 10, 60, "10 per leg"));
        plan.addExercise(exercise(2, "Brisk Walk", 1, 1, 0, "30 minutes"));
        plan.addExercise(exercise(3, "Glute Bridges", 3, 15, 45, null));
        plan.addExercise(exercise(3, "Bent-over Rows", 3, 12, 60, "Flat back"));
        workoutPlanRepository.save(plan);
    }

    private WorkoutExercise exercise(int day, String name, int sets, int reps, int rest, String notes) {
        WorkoutExercise exercise = new WorkoutExercise();
        exercise.setDayNumber(day);
        exercise.setName(name);
        exercise.setSets(sets);
        exercise.setReps(reps);
        exercise.setRestSeconds(rest);
        exercise.setNotes(notes);
        return exercise;
    }

    /**
     * 28 days of history ending today. A few rest days break the routine (but the last 8 days
     * are consecutive, so the streak shows 8). Weight slowly drops from about 78 kg to 75.5 kg,
     * and every few workouts there is also a pose detection session whose form accuracy improves.
     */
    private void createProgress(User user) {
        List<Integer> restDays = List.of(26, 22, 19, 15, 12, 8); // "days ago"
        LocalDate today = LocalDate.now();
        int workoutCount = 0;
        for (int daysAgo = 27; daysAgo >= 0; daysAgo--) {
            if (restDays.contains(daysAgo)) {
                continue;
            }
            workoutCount++;
            LocalDate date = today.minusDays(daysAgo);
            int minutes = 30 + (daysAgo * 7) % 31;               // 30-60 minutes, varied but repeatable
            double weight = 75.5 + daysAgo * 0.09 + ((daysAgo % 3) - 1) * 0.15;

            ProgressEntry manual = new ProgressEntry();
            manual.setUser(user);
            manual.setDate(date);
            manual.setWeightKg(Math.round(weight * 10) / 10.0);
            manual.setWorkoutMinutes(minutes);
            manual.setCaloriesBurned(minutes * 7);
            manual.setSource(ProgressSource.MANUAL);
            manual.setNotes(workoutCount % 5 == 0 ? "Felt strong today" : null);
            progressRepository.save(manual);

            if (workoutCount % 3 == 0) {
                boolean squat = (workoutCount / 3) % 2 == 1;
                ProgressEntry pose = new ProgressEntry();
                pose.setUser(user);
                pose.setDate(date);
                pose.setSource(ProgressSource.POSE_DETECTION);
                pose.setExercise(squat ? "SQUAT" : "BICEP_CURL");
                pose.setReps(squat ? 15 + workoutCount / 2 : 20 + workoutCount / 2);
                pose.setWorkoutMinutes(10);
                pose.setCaloriesBurned(squat ? 60 : 40);
                // Form accuracy improves from about 68% to about 92% over the four weeks.
                pose.setCorrectFormPercent(Math.round((68 + (27 - daysAgo) * 0.9) * 10) / 10.0);
                progressRepository.save(pose);
            }
        }
    }

    // ----- small date helpers -----

    /** The next given weekday (strictly after today) at the given time. */
    private LocalDateTime next(DayOfWeek day, String time) {
        return LocalDate.now().with(TemporalAdjusters.next(day)).atTime(LocalTime.parse(time));
    }

    /** The previous given weekday (strictly before today) at the given time. */
    private LocalDateTime previous(DayOfWeek day, String time) {
        return LocalDate.now().with(TemporalAdjusters.previous(day)).atTime(LocalTime.parse(time));
    }
}
