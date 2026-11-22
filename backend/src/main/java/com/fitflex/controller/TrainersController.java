package com.fitflex.controller;

import com.fitflex.dto.response.TrainerResponse;
import com.fitflex.service.TrainerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * PUBLIC trainer directory under /api/trainers (plural): anyone can browse and filter trainers.
 * Example: GET /api/trainers?specialization=Yoga&minExperience=3&maxPrice=800&search=priya
 */
@RestController
@RequestMapping("/api/trainers")
public class TrainersController {

    private final TrainerService trainerService;

    public TrainersController(TrainerService trainerService) {
        this.trainerService = trainerService;
    }

    @GetMapping
    public List<TrainerResponse> search(@RequestParam(required = false) String specialization,
                                        @RequestParam(required = false) Integer minExperience,
                                        @RequestParam(required = false) BigDecimal maxPrice,
                                        @RequestParam(required = false) String search) {
        return trainerService.search(specialization, minExperience, maxPrice, search);
    }

    @GetMapping("/{id}")
    public TrainerResponse getById(@PathVariable Long id) {
        return trainerService.getPublicProfile(id);
    }
}
