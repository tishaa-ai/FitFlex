package com.fitflex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Finds gyms with Google "Places API (New)" Nearby Search:
 * POST https://places.googleapis.com/v1/places:searchNearby with includedTypes = ["gym"].
 * The field mask header asks Google for only the fields we use (cheaper and faster).
 */
@Component
public class GooglePlacesClient {

    private static final String URL = "https://places.googleapis.com/v1/places:searchNearby";
    private static final String FIELD_MASK = "places.displayName,places.formattedAddress,places.location,places.rating";

    /** Raw place before distance is added. */
    public record Place(String name, String address, double lat, double lng, Double rating) {
    }

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public GooglePlacesClient(RestClient externalApiClient, ObjectMapper objectMapper,
                              @Value("${fitflex.google-maps.api-key:}") String apiKey) {
        this.restClient = externalApiClient;
        this.objectMapper = objectMapper;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean isConfigured() {
        return !apiKey.isEmpty();
    }

    public List<Place> searchGyms(double lat, double lng, int radiusMeters) {
        Map<String, Object> body = Map.of(
                "includedTypes", List.of("gym"),
                "maxResultCount", 20,
                "locationRestriction", Map.of("circle", Map.of(
                        "center", Map.of("latitude", lat, "longitude", lng),
                        "radius", (double) radiusMeters)));

        String json = restClient.post()
                .uri(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Goog-Api-Key", apiKey)
                .header("X-Goog-FieldMask", FIELD_MASK)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);

        List<Place> places = new ArrayList<>();
        for (JsonNode p : objectMapper.readTree(json).path("places")) {
            JsonNode rating = p.path("rating");
            places.add(new Place(
                    p.path("displayName").path("text").asString("Gym"),
                    p.path("formattedAddress").asString(""),
                    p.path("location").path("latitude").asDouble(),
                    p.path("location").path("longitude").asDouble(),
                    rating.isMissingNode() ? null : rating.asDouble()));
        }
        return places;
    }
}
