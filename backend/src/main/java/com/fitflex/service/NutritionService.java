package com.fitflex.service;

import com.fitflex.dto.request.DietGenerateRequest;
import com.fitflex.dto.response.DietPlanResponse;
import com.fitflex.dto.response.DietPlanResponse.Meal;
import com.fitflex.dto.response.DietPlanResponse.MealGroup;
import com.fitflex.dto.response.DietPlanResponse.Nutrients;
import com.fitflex.dto.response.FoodSearchResponse;
import com.fitflex.entity.ActivityLevel;
import com.fitflex.entity.DietPlan;
import com.fitflex.entity.DietSource;
import com.fitflex.entity.FitnessGoal;
import com.fitflex.entity.Gender;
import com.fitflex.entity.User;
import com.fitflex.exception.BadRequestException;
import com.fitflex.repository.DietPlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Diet planning:
 * 1. BMR with the Mifflin-St Jeor equation, from the user's profile.
 * 2. TDEE = BMR x activity factor.
 * 3. Target calories = TDEE - 500 (weight loss), + 300 (muscle gain) or + 0 (maintenance).
 * 4. Meals from Spoonacular when an API key is set; otherwise, or if the call fails, from the
 *    local Indian meal dataset. The plan is saved as a DietPlan with its meals stored as JSON.
 */
@Service
public class NutritionService {

    private static final Logger log = LoggerFactory.getLogger(NutritionService.class);

    static final int WEIGHT_LOSS_DEFICIT = 500;
    static final int MUSCLE_GAIN_SURPLUS = 300;
    /** Safety floor: plans below ~1200 kcal/day are not advisable without medical supervision. */
    static final int MINIMUM_CALORIES = 1200;

    private final DietPlanRepository dietPlanRepository;
    private final UserService userService;
    private final SpoonacularClient spoonacularClient;
    private final LocalMealDataset localMealDataset;
    private final ObjectMapper objectMapper;

    public NutritionService(DietPlanRepository dietPlanRepository, UserService userService,
                            SpoonacularClient spoonacularClient, LocalMealDataset localMealDataset,
                            ObjectMapper objectMapper) {
        this.dietPlanRepository = dietPlanRepository;
        this.userService = userService;
        this.spoonacularClient = spoonacularClient;
        this.localMealDataset = localMealDataset;
        this.objectMapper = objectMapper;
    }

    /** What we keep in DietPlan.planJson. */
    record StoredPlan(String note, List<Meal> meals, Nutrients totals) {
    }

    /** One meal slot of the day and its share of the daily calories. */
    record MealSlot(String mealType, double calorieShare) {
    }

    // ===================== the calorie maths =====================

    /**
     * Mifflin-St Jeor (1990): BMR = 10 x weight(kg) + 6.25 x height(cm) - 5 x age(years) + s,
     * where s = +5 for men and -161 for women. For OTHER we use the average of the two (-78).
     */
    public double calculateBmr(double weightKg, double heightCm, int age, Gender gender) {
        double genderConstant = switch (gender) {
            case MALE -> 5;
            case FEMALE -> -161;
            case OTHER -> -78;
        };
        return 10 * weightKg + 6.25 * heightCm - 5 * age + genderConstant;
    }

    /** TDEE = BMR x activity multiplier (1.2 sedentary ... 1.9 very active). */
    public double calculateTdee(double bmr, ActivityLevel activityLevel) {
        return bmr * activityLevel.getFactor();
    }

    public int calculateTargetCalories(double tdee, FitnessGoal goal) {
        int adjustment = switch (goal) {
            case WEIGHT_LOSS -> -WEIGHT_LOSS_DEFICIT;
            case MUSCLE_GAIN -> MUSCLE_GAIN_SURPLUS;
            case MAINTENANCE -> 0;
        };
        return Math.max(MINIMUM_CALORIES, (int) Math.round(tdee + adjustment));
    }

    // ===================== generating a plan =====================

    @Transactional
    public DietPlanResponse generate(Long userId, DietGenerateRequest request) {
        User user = userService.getUserEntity(userId);
        requireCompleteProfile(user);

        DietType dietType = DietType.fromLabel(request.dietType());
        FitnessGoal goal = request.goal() != null ? request.goal() : user.getFitnessGoal();
        if (goal == null) {
            throw new BadRequestException("Choose a goal (WEIGHT_LOSS, MUSCLE_GAIN or MAINTENANCE)");
        }
        int mealsPerDay = request.mealsPerDay() != null ? request.mealsPerDay() : 3;

        // Keep full precision for the maths and round only what we store/show.
        double bmr = calculateBmr(user.getWeightKg(), user.getHeightCm(), user.getAge(), user.getGender());
        double tdee = calculateTdee(bmr, user.getActivityLevel());
        int target = calculateTargetCalories(tdee, goal);

        List<MealSlot> slots = slotsFor(mealsPerDay);
        List<Meal> meals;
        DietSource source;
        String note;

        if (!spoonacularClient.isConfigured()) {
            meals = buildLocalPlan(slots, dietType, target);
            source = DietSource.LOCAL_FALLBACK;
            note = "No Spoonacular API key is configured, so this plan uses the FitFlex Indian meal dataset.";
        } else {
            try {
                meals = buildSpoonacularPlan(slots, dietType, target);
                source = DietSource.SPOONACULAR;
                note = mealsPerDay > 3
                        ? "Main meals from Spoonacular; snacks from the FitFlex dataset."
                        : "Meals from Spoonacular.";
            } catch (RuntimeException e) {
                // Why catch everything: whatever went wrong outside (timeout, quota, bad JSON),
                // the user should still get a plan.
                log.warn("Spoonacular failed, using local fallback: {}", e.getMessage());
                meals = buildLocalPlan(slots, dietType, target);
                source = DietSource.LOCAL_FALLBACK;
                note = "Spoonacular was unavailable, so this plan uses the FitFlex Indian meal dataset.";
            }
        }

        Nutrients totals = totalsOf(meals);

        DietPlan plan = new DietPlan();
        plan.setUser(user);
        plan.setGoal(goal);
        plan.setDietType(dietType.getLabel());
        plan.setMealsPerDay(mealsPerDay);
        plan.setBmr(round1(bmr));
        plan.setTdee(round1(tdee));
        plan.setTargetCalories(target);
        plan.setSource(source);
        plan.setPlanJson(objectMapper.writeValueAsString(new StoredPlan(note, meals, totals)));
        return toResponse(dietPlanRepository.save(plan));
    }

    /** All of the user's plans, newest first. */
    @Transactional(readOnly = true)
    public List<DietPlanResponse> getHistory(Long userId) {
        return dietPlanRepository.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::toResponse).toList();
    }

    // ===================== food search =====================

    @Transactional(readOnly = true)
    public FoodSearchResponse searchFood(String query) {
        if (query == null || query.trim().length() < 2) {
            throw new BadRequestException("Type at least 2 characters to search");
        }
        String q = query.trim();
        if (spoonacularClient.isConfigured()) {
            try {
                return new FoodSearchResponse("SPOONACULAR", null, spoonacularClient.searchFood(q, 5));
            } catch (RuntimeException e) {
                log.warn("Spoonacular food search failed, using local data: {}", e.getMessage());
                return new FoodSearchResponse("LOCAL_FALLBACK",
                        "Spoonacular was unavailable, showing results from the FitFlex food list.",
                        localMealDataset.searchFoods(q, 15));
            }
        }
        return new FoodSearchResponse("LOCAL_FALLBACK",
                "Showing results from the FitFlex food list (no Spoonacular key configured).",
                localMealDataset.searchFoods(q, 15));
    }

    // ===================== helpers =====================

    /** How the day's calories are shared between meals (shares add up to 1.0). */
    static List<MealSlot> slotsFor(int mealsPerDay) {
        return switch (mealsPerDay) {
            case 4 -> List.of(new MealSlot("BREAKFAST", 0.25), new MealSlot("LUNCH", 0.35),
                    new MealSlot("SNACK", 0.10), new MealSlot("DINNER", 0.30));
            case 5 -> List.of(new MealSlot("BREAKFAST", 0.25), new MealSlot("SNACK", 0.10),
                    new MealSlot("LUNCH", 0.30), new MealSlot("SNACK", 0.10), new MealSlot("DINNER", 0.25));
            default -> List.of(new MealSlot("BREAKFAST", 0.30), new MealSlot("LUNCH", 0.40),
                    new MealSlot("DINNER", 0.30));
        };
    }

    private List<Meal> buildLocalPlan(List<MealSlot> slots, DietType diet, int target) {
        Set<String> used = new HashSet<>();
        List<Meal> meals = new ArrayList<>();
        for (MealSlot slot : slots) {
            Meal meal = localMealDataset.pickMeal(slot.mealType(), diet, (int) Math.round(target * slot.calorieShare()), used);
            used.add(meal.name());
            meals.add(meal);
        }
        return meals;
    }

    /** Spoonacular supplies breakfast, lunch and dinner; any snack slots come from the local dataset. */
    private List<Meal> buildSpoonacularPlan(List<MealSlot> slots, DietType diet, int target) {
        double mainShare = slots.stream().filter(s -> !s.mealType().equals("SNACK")).mapToDouble(MealSlot::calorieShare).sum();
        List<Meal> mainMeals = spoonacularClient.generateDayPlan((int) Math.round(target * mainShare), diet.getSpoonacularDiet());

        Set<String> used = new HashSet<>();
        List<Meal> meals = new ArrayList<>();
        for (MealSlot slot : slots) {
            if (slot.mealType().equals("SNACK")) {
                Meal snack = localMealDataset.pickMeal("SNACK", diet, (int) Math.round(target * slot.calorieShare()), used);
                used.add(snack.name());
                meals.add(snack);
            } else {
                mainMeals.stream().filter(m -> m.mealType().equals(slot.mealType())).findFirst().ifPresent(meals::add);
            }
        }
        return meals;
    }

    private Nutrients totalsOf(List<Meal> meals) {
        int calories = meals.stream().mapToInt(Meal::calories).sum();
        double protein = meals.stream().mapToDouble(Meal::protein).sum();
        double carbs = meals.stream().mapToDouble(Meal::carbs).sum();
        double fat = meals.stream().mapToDouble(Meal::fat).sum();
        return new Nutrients(calories, round1(protein), round1(carbs), round1(fat));
    }

    private DietPlanResponse toResponse(DietPlan plan) {
        StoredPlan stored = objectMapper.readValue(plan.getPlanJson(), StoredPlan.class);

        // Group meals by type, keeping the order of the day (breakfast first, dinner last).
        Map<String, List<Meal>> grouped = new LinkedHashMap<>();
        for (String type : List.of("BREAKFAST", "LUNCH", "SNACK", "DINNER")) {
            List<Meal> ofType = stored.meals().stream().filter(m -> m.mealType().equals(type)).toList();
            if (!ofType.isEmpty()) {
                grouped.put(type, ofType);
            }
        }
        List<MealGroup> groups = grouped.entrySet().stream()
                .map(e -> new MealGroup(e.getKey(), e.getValue()))
                .toList();

        return new DietPlanResponse(plan.getId(), plan.getGoal(), plan.getDietType(), plan.getMealsPerDay(),
                plan.getBmr(), plan.getTdee(), plan.getTargetCalories(), plan.getSource(), stored.note(),
                plan.getCreatedAt(), groups, stored.totals());
    }

    private void requireCompleteProfile(User user) {
        if (user.getWeightKg() == null || user.getHeightCm() == null || user.getAge() == null
                || user.getGender() == null || user.getActivityLevel() == null) {
            throw new BadRequestException("Please complete your profile (age, gender, height, weight, activity level) first");
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}
