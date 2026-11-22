package com.fitflex.dto.response;

import java.util.List;

/**
 * Result of GET /api/gym/nearby. {@code source} is GOOGLE_PLACES, OPENSTREETMAP or NONE
 * (when every provider failed; {@code message} then explains why the list is empty).
 */
public record GymSearchResponse(String source, String message, List<Gym> gyms) {

    /** A gym in the same normalized shape whichever provider found it. Rating is null for OpenStreetMap. */
    public record Gym(String name, String address, double lat, double lng, Double rating,
                      double distanceKm, String directionsUrl) {
    }
}
