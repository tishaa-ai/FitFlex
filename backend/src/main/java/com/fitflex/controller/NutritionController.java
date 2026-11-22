package com.fitflex.controller;

import com.fitflex.dto.request.DietGenerateRequest;
import com.fitflex.dto.response.DietPlanResponse;
import com.fitflex.dto.response.FoodSearchResponse;
import com.fitflex.security.UserPrincipal;
import com.fitflex.service.NutritionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Diet plans and food search for USERs (Spoonacular with a local fallback). */
@RestController
@RequestMapping("/api/diet")
public class NutritionController {

    private final NutritionService nutritionService;

    public NutritionController(NutritionService nutritionService) {
        this.nutritionService = nutritionService;
    }

    @PostMapping("/generate")
    @ResponseStatus(HttpStatus.CREATED)
    public DietPlanResponse generate(@AuthenticationPrincipal UserPrincipal me, @Valid @RequestBody DietGenerateRequest request) {
        return nutritionService.generate(me.getId(), request);
    }

    @GetMapping("/my")
    public List<DietPlanResponse> history(@AuthenticationPrincipal UserPrincipal me) {
        return nutritionService.getHistory(me.getId());
    }

    /** GET /api/diet/search-food?query=paneer */
    @GetMapping("/search-food")
    public FoodSearchResponse searchFood(@RequestParam String query) {
        return nutritionService.searchFood(query);
    }
}
