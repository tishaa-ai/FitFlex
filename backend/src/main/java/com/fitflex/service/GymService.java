package com.fitflex.service;

import com.fitflex.dto.response.GymSearchResponse;
import com.fitflex.dto.response.GymSearchResponse.Gym;
import com.fitflex.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nearby gym search with a chain of fallbacks:
 * Google Places (only if GOOGLE_MAPS_API_KEY is set) -> OpenStreetMap Overpass -> OpenStreetMap
 * Nominatim -> an empty list with an explanation. Results are normalized, given a straight-line
 * distance, sorted nearest first, and cached for 10 minutes to go easy on the free APIs.
 */
@Service
public class GymService {

    private static final Logger log = LoggerFactory.getLogger(GymService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_RESULTS = 30;

    private final GooglePlacesClient googlePlacesClient;
    private final OverpassClient overpassClient;
    private final NominatimClient nominatimClient;

    private record CacheEntry(GymSearchResponse response, Instant createdAt) {
    }

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public GymService(GooglePlacesClient googlePlacesClient, OverpassClient overpassClient,
                      NominatimClient nominatimClient) {
        this.googlePlacesClient = googlePlacesClient;
        this.overpassClient = overpassClient;
        this.nominatimClient = nominatimClient;
    }

    public GymSearchResponse findNearby(double lat, double lng, int radiusMeters) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("Latitude must be -90..90 and longitude -180..180");
        }
        if (radiusMeters < 200 || radiusMeters > 20000) {
            throw new BadRequestException("Radius must be between 200 and 20000 metres");
        }

        // ~100 m precision is plenty for a cache key.
        String key = String.format(Locale.ROOT, "%.3f,%.3f,%d", lat, lng, radiusMeters);
        CacheEntry cached = cache.get(key);
        if (cached != null && cached.createdAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.response();
        }

        GymSearchResponse response = search(lat, lng, radiusMeters);
        if (!"NONE".equals(response.source())) {
            cache.put(key, new CacheEntry(response, Instant.now()));
        }
        return response;
    }

    private GymSearchResponse search(double lat, double lng, int radius) {
        String googleNote = null;
        if (googlePlacesClient.isConfigured()) {
            try {
                return new GymSearchResponse("GOOGLE_PLACES", null, normalize(googlePlacesClient.searchGyms(lat, lng, radius), lat, lng, radius));
            } catch (RuntimeException e) {
                log.warn("Google Places failed, falling back to OpenStreetMap: {}", e.getMessage());
                googleNote = "Google Places was unavailable, showing OpenStreetMap results.";
            }
        }
        try {
            List<Gym> gyms = normalize(overpassClient.searchGyms(lat, lng, radius), lat, lng, radius);
            String message = googleNote != null ? googleNote
                    : gyms.isEmpty() ? "No gyms are mapped on OpenStreetMap in this area. Try a bigger radius." : null;
            return new GymSearchResponse("OPENSTREETMAP", message, gyms);
        } catch (RuntimeException e) {
            log.warn("OpenStreetMap Overpass failed, trying Nominatim: {}", e.getMessage());
        }
        try {
            List<Gym> gyms = normalize(nominatimClient.searchGyms(lat, lng, radius), lat, lng, radius);
            String message = "The main OpenStreetMap gym search is busy, so these results come from a name search"
                    + " and may be incomplete.";
            return new GymSearchResponse("OPENSTREETMAP", message, gyms);
        } catch (RuntimeException e) {
            log.warn("OpenStreetMap Nominatim failed: {}", e.getMessage());
            return new GymSearchResponse("NONE",
                    "Gym search is unavailable right now (no internet connection or the map services are busy). Please try again later.",
                    List.of());
        }
    }

    private List<Gym> normalize(List<GooglePlacesClient.Place> places, double lat, double lng, int radiusMeters) {
        double maxKm = radiusMeters / 1000.0;
        return places.stream()
                .map(p -> new Gym(p.name(), p.address(), p.lat(), p.lng(), p.rating(),
                        round2(distanceKm(lat, lng, p.lat(), p.lng())),
                        "https://www.google.com/maps/dir/?api=1&destination=" + p.lat() + "," + p.lng()))
                .filter(g -> g.distanceKm() <= maxKm) // a bounding box has corners outside the circle
                .sorted(Comparator.comparingDouble(Gym::distanceKm))
                .limit(MAX_RESULTS)
                .toList();
    }

    /**
     * Haversine formula: straight-line ("as the crow flies") distance between two points on a
     * sphere, given their latitude/longitude in degrees.
     */
    static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static double round2(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
