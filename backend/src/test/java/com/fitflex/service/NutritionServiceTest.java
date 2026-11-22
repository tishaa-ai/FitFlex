package com.fitflex.service;

import com.fitflex.dto.request.DietGenerateRequest;
import com.fitflex.dto.response.DietPlanResponse;
import com.fitflex.dto.response.DietPlanResponse.Meal;
import com.fitflex.entity.ActivityLevel;
import com.fitflex.entity.DietPlan;
import com.fitflex.entity.DietSource;
import com.fitflex.entity.FitnessGoal;
import com.fitflex.entity.FitnessLevel;
import com.fitflex.entity.Gender;
import com.fitflex.entity.User;
import com.fitflex.exception.BadRequestException;
import com.fitflex.repository.DietPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link NutritionService}: the Mifflin-St Jeor / TDEE / target maths with
 * hand-calculated values, and the Spoonacular -> local dataset fallback.
 * The real {@link LocalMealDataset} is used (it reads meals.json from the classpath);
 * Spoonacular is a Mockito mock, so no network calls are made.
 */
@ExtendWith(MockitoExtension.class)
class NutritionServiceTest {

    @Mock
    private DietPlanRepository dietPlanRepository;
    @Mock
    private UserService userService;
    @Mock
    private SpoonacularClient spoonacularClient;

    private NutritionService nutritionService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = JsonMapper.builder().build();
        nutritionService = new NutritionService(dietPlanRepository, userService, spoonacularClient,
                new LocalMealDataset(objectMapper), objectMapper);
        // save() just hands back the plan it was given (with an id), like the real repository.
        lenient().when(dietPlanRepository.save(any(DietPlan.class))).thenAnswer(invocation -> {
            DietPlan plan = invocation.getArgument(0);
            plan.setId(1L);
            return plan;
        });
    }

    private User user(double weight, double height, int age, Gender gender, ActivityLevel activity) {
        User user = new User();
        user.setId(1L);
        user.setFullName("Test User");
        user.setWeightKg(weight);
        user.setHeightCm(height);
        user.setAge(age);
        user.setGender(gender);
        user.setActivityLevel(activity);
        user.setFitnessGoal(FitnessGoal.WEIGHT_LOSS);
        user.setFitnessLevel(FitnessLevel.BEGINNER);
        return user;
    }

    // ===================== the maths =====================

    @Test
    void bmrForManMatchesMifflinStJeor() {
        // 10*70 + 6.25*175 - 5*25 + 5 = 700 + 1093.75 - 125 + 5
        assertThat(nutritionService.calculateBmr(70, 175, 25, Gender.MALE)).isEqualTo(1673.75);
    }

    @Test
    void bmrForWomanMatchesMifflinStJeor() {
        // 10*60 + 6.25*165 - 5*30 - 161 = 600 + 1031.25 - 150 - 161
        assertThat(nutritionService.calculateBmr(60, 165, 30, Gender.FEMALE)).isEqualTo(1320.25);
    }

    @Test
    void tdeeMultipliesBmrByActivityFactor() {
        assertThat(nutritionService.calculateTdee(1673.75, ActivityLevel.MODERATE)).isCloseTo(2594.3125, within(1e-9));
        assertThat(nutritionService.calculateTdee(1320.25, ActivityLevel.SEDENTARY)).isCloseTo(1584.3, within(1e-9));
    }

    @Test
    void targetCaloriesDependOnGoal() {
        double tdee = 2594.3125;
        assertThat(nutritionService.calculateTargetCalories(tdee, FitnessGoal.WEIGHT_LOSS)).isEqualTo(2094);
        assertThat(nutritionService.calculateTargetCalories(tdee, FitnessGoal.MUSCLE_GAIN)).isEqualTo(2894);
        assertThat(nutritionService.calculateTargetCalories(tdee, FitnessGoal.MAINTENANCE)).isEqualTo(2594);
    }

    @Test
    void targetCaloriesNeverGoBelowSafetyFloor() {
        assertThat(nutritionService.calculateTargetCalories(1450, FitnessGoal.WEIGHT_LOSS)).isEqualTo(1200);
    }

    // ===================== fallback behaviour =====================

    @Test
    void usesLocalDatasetWhenNoApiKeyIsConfigured() {
        when(userService.getUserEntity(1L)).thenReturn(user(70, 175, 25, Gender.MALE, ActivityLevel.MODERATE));
        when(spoonacularClient.isConfigured()).thenReturn(false);

        DietPlanResponse plan = nutritionService.generate(1L, new DietGenerateRequest(FitnessGoal.WEIGHT_LOSS, "vegan", 4));

        assertThat(plan.source()).isEqualTo(DietSource.LOCAL_FALLBACK);
        assertThat(plan.note()).contains("No Spoonacular API key");
        assertThat(plan.bmr()).isEqualTo(1673.8);          // rounded to 1 decimal for display
        assertThat(plan.tdee()).isEqualTo(2594.3);
        assertThat(plan.targetCalories()).isEqualTo(2094);
        assertThat(plan.mealGroups()).extracting(DietPlanResponse.MealGroup::mealType)
                .containsExactly("BREAKFAST", "LUNCH", "SNACK", "DINNER");
        List<Meal> meals = plan.mealGroups().stream().flatMap(g -> g.meals().stream()).toList();
        assertThat(meals).hasSize(4).allMatch(m -> m.origin().equals(LocalMealDataset.ORIGIN));
        // Portion scaling keeps the day's total near the target.
        assertThat(plan.totals().calories()).isCloseTo(2094, within(250));
        verify(spoonacularClient, never()).generateDayPlan(anyInt(), any());
    }

    @Test
    void fallsBackToLocalDatasetWhenSpoonacularFails() {
        when(userService.getUserEntity(1L)).thenReturn(user(60, 165, 30, Gender.FEMALE, ActivityLevel.LIGHT));
        when(spoonacularClient.isConfigured()).thenReturn(true);
        when(spoonacularClient.generateDayPlan(anyInt(), eq("vegetarian")))
                .thenThrow(new IllegalStateException("402 Payment Required: daily quota used up"));

        DietPlanResponse plan = nutritionService.generate(1L, new DietGenerateRequest(FitnessGoal.MAINTENANCE, "vegetarian", 3));

        assertThat(plan.source()).isEqualTo(DietSource.LOCAL_FALLBACK);
        assertThat(plan.note()).contains("unavailable");
        assertThat(plan.mealGroups()).hasSize(3);
    }

    @Test
    void usesSpoonacularMealsWhenAvailable() {
        when(userService.getUserEntity(1L)).thenReturn(user(70, 175, 25, Gender.MALE, ActivityLevel.MODERATE));
        when(spoonacularClient.isConfigured()).thenReturn(true);
        when(spoonacularClient.generateDayPlan(anyInt(), any())).thenReturn(List.of(
                new Meal("BREAKFAST", "Oat Pancakes", "1 serving", 600, 20, 90, 15, null, null, 20, "Spoonacular"),
                new Meal("LUNCH", "Chicken Bowl", "1 serving", 800, 50, 80, 25, null, null, 30, "Spoonacular"),
                new Meal("DINNER", "Fish Curry", "1 serving", 700, 45, 60, 25, null, null, 35, "Spoonacular")));

        DietPlanResponse plan = nutritionService.generate(1L, new DietGenerateRequest(FitnessGoal.MAINTENANCE, "non-veg", 4));

        assertThat(plan.source()).isEqualTo(DietSource.SPOONACULAR);
        // Spoonacular has no snacks, so the 4th meal comes from the local dataset.
        Meal snack = plan.mealGroups().stream().filter(g -> g.mealType().equals("SNACK")).findFirst().orElseThrow().meals().get(0);
        assertThat(snack.origin()).isEqualTo(LocalMealDataset.ORIGIN);
        // Spoonacular was asked only for the main meals' share (90% of the target).
        verify(spoonacularClient).generateDayPlan(eq((int) Math.round(2594 * 0.90)), eq(null));
    }

    @Test
    void incompleteProfileIsRejected() {
        User incomplete = user(70, 175, 25, Gender.MALE, ActivityLevel.MODERATE);
        incomplete.setHeightCm(null);
        when(userService.getUserEntity(1L)).thenReturn(incomplete);

        assertThatThrownBy(() -> nutritionService.generate(1L, new DietGenerateRequest(null, "vegan", 3)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("complete your profile");
    }

    @Test
    void unknownDietTypeIsRejected() {
        assertThatThrownBy(() -> DietType.fromLabel("keto")).isInstanceOf(BadRequestException.class);
        assertThat(DietType.fromLabel("Non_Veg")).isEqualTo(DietType.NON_VEG);
    }

    @Test
    void foodSearchFallsBackToLocalList() {
        when(spoonacularClient.isConfigured()).thenReturn(true);
        when(spoonacularClient.searchFood(anyString(), anyInt())).thenThrow(new IllegalStateException("timeout"));

        var result = nutritionService.searchFood("paneer");

        assertThat(result.source()).isEqualTo("LOCAL_FALLBACK");
        assertThat(result.items()).isNotEmpty().allMatch(i -> i.name().toLowerCase().contains("paneer"));
    }
}
