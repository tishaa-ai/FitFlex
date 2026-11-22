package com.fitflex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Second free OpenStreetMap source, used when Overpass is busy: the Nominatim search API.
 * Nominatim searches by name inside a bounding box, so it finds fewer gyms than Overpass,
 * but it is usually fast and available. Usage policy: max 1 request per second and an
 * identifying User-Agent (set in RestClientConfig).
 */
@Component
public class NominatimClient {

    private static final String[] SEARCH_TERMS = {"fitness", "gym"};

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public NominatimClient(RestClient externalApiClient, ObjectMapper objectMapper,
                           @Value("${fitflex.nominatim.url:https://nominatim.openstreetmap.org/search}") String baseUrl) {
        this.restClient = externalApiClient;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
    }

    public List<GooglePlacesClient.Place> searchGyms(double lat, double lng, int radiusMeters) {
        // Convert the radius into a lat/lng box: 1 degree of latitude is about 111.32 km,
        // a degree of longitude shrinks with cos(latitude).
        double dLat = radiusMeters / 111_320.0;
        double dLng = radiusMeters / (111_320.0 * Math.cos(Math.toRadians(lat)));
        String viewbox = String.format(Locale.ROOT, "%f,%f,%f,%f", lng - dLng, lat + dLat, lng + dLng, lat - dLat);

        // Keyed by OSM id so a place found by both search terms appears once.
        Map<String, GooglePlacesClient.Place> found = new LinkedHashMap<>();
        for (int i = 0; i < SEARCH_TERMS.length; i++) {
            if (i > 0) {
                pauseForRateLimit();
            }
            String url = baseUrl + "?format=jsonv2&limit=40&bounded=1&viewbox=" + viewbox + "&q=" + SEARCH_TERMS[i];
            String json = restClient.get().uri(url).retrieve().body(String.class);
            for (JsonNode place : objectMapper.readTree(json)) {
                String category = place.path("category").asString("") + "=" + place.path("type").asString("");
                String name = place.path("name").asString("");
                boolean looksLikeGym = category.equals("leisure=fitness_centre") || category.equals("leisure=sports_centre")
                        || name.toLowerCase(Locale.ROOT).matches(".*\\b(gym|fitness)\\b.*");
                if (!looksLikeGym || name.isBlank()) {
                    continue;
                }
                found.put(place.path("osm_type").asString("") + place.path("osm_id").asString(""),
                        new GooglePlacesClient.Place(name, shortenAddress(place.path("display_name").asString(""), name),
                                place.path("lat").asDouble(), place.path("lon").asDouble(), null));
            }
        }
        return List.copyOf(found.values());
    }

    /** display_name repeats the place name and ends with the country; trim both for a tidy address. */
    private String shortenAddress(String displayName, String name) {
        String address = displayName.startsWith(name + ", ") ? displayName.substring(name.length() + 2) : displayName;
        return address.replaceFirst(",\\s*India$", "");
    }

    private void pauseForRateLimit() {
        try {
            Thread.sleep(1100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
