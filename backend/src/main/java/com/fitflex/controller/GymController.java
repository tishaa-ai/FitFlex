package com.fitflex.controller;

import com.fitflex.dto.response.GymSearchResponse;
import com.fitflex.service.GymService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** PUBLIC nearby gym search: GET /api/gym/nearby?lat=21.1458&lng=79.0882&radius=3000 */
@RestController
@RequestMapping("/api/gym")
public class GymController {

    private final GymService gymService;

    public GymController(GymService gymService) {
        this.gymService = gymService;
    }

    @GetMapping("/nearby")
    public GymSearchResponse nearby(@RequestParam double lat, @RequestParam double lng,
                                    @RequestParam(defaultValue = "3000") int radius) {
        return gymService.findNearby(lat, lng, radius);
    }
}
