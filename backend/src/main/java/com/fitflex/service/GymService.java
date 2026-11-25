package com.fitflex.service;

import com.fitflex.dto.response.GymSearchResponse;
import com.fitflex.dto.response.GymSearchResponse.Gym;
import com.fitflex.exception.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Nearby gym search, built to keep working when the free map services are slow or down.
 *
 * Live providers, in order:
 *   Google Places (only if GOOGLE_MAPS_API_KEY is set)
 *   -> OpenStreetMap Overpass (several mirrors in parallel, with retries; see OverpassClient)
 *   -> OpenStreetMap Nominatim (name search, fewer results but usually fast).
 * Extra safety nets:
 *   - If nothing is found within the radius, the nearest gyms within 10 km are shown instead.
 *   - If Overpass just failed, it is skipped for a few minutes ("circuit breaker") so users
 *     don't wait 30 s for a server we already know is down.
 *   - Good results are kept in memory for an hour and saved to disk (GymCacheStore); if every
 *     live provider fails, the saved results are shown with the date they were found.
 *   - On startup the default city is searched in the background, so the first visit is instant.
 */
@Service
public class GymService {

    private static final Logger log = LoggerFactory.getLogger(GymService.class);
    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final Duration MEMORY_CACHE_TTL = Duration.ofHours(1);
    private static final Duration OVERPASS_COOL_DOWN = Duration.ofMinutes(3);
    private static final Duration WARM_UP_MAX_AGE = Duration.ofHours(12);
    private static final DateTimeFormatter SAVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());

    static final int MAX_RESULTS = 30;
    /** When nothing is found within the user's radius, look this far instead. */
    static final int EXPANDED_RADIUS = 10_000;
    /** How many of the "nearest within 10 km" gyms to show in that case. */
    static final int EXPANDED_SHOWN = 10;

    private final GooglePlacesClient googlePlacesClient;
    private final OverpassClient overpassClient;
    private final NominatimClient nominatimClient;
    private final GymCacheStore cacheStore;
    private final boolean warmUpEnabled;
    private final double defaultLat;
    private final double defaultLng;

    private record CacheEntry(GymSearchResponse response, Instant createdAt) {
    }

    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();
    /** Circuit breaker: while now < this time, Overpass is skipped. */
    private volatile Instant skipOverpassUntil = Instant.MIN;

    public GymService(GooglePlacesClient googlePlacesClient, OverpassClient overpassClient,
                      NominatimClient nominatimClient, GymCacheStore cacheStore,
                      @Value("${fitflex.gym.warm-up:true}") boolean warmUpEnabled,
                      @Value("${fitflex.gym.default-lat:21.1458}") double defaultLat,
                      @Value("${fitflex.gym.default-lng:79.0882}") double defaultLng) {
        this.googlePlacesClient = googlePlacesClient;
        this.overpassClient = overpassClient;
        this.nominatimClient = nominatimClient;
        this.cacheStore = cacheStore;
        this.warmUpEnabled = warmUpEnabled;
        this.defaultLat = defaultLat;
        this.defaultLng = defaultLng;
    }

    // ===================== public entry point =====================

    public GymSearchResponse findNearby(double lat, double lng, int radiusMeters) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("Latitude must be -90..90 and longitude -180..180");
        }
        if (radiusMeters < 200 || radiusMeters > 20000) {
            throw new BadRequestException("Radius must be between 200 and 20000 metres");
        }

        GymSearchResponse live = searchWithCache(lat, lng, radiusMeters);
        if (!live.gyms().isEmpty()) {
            return live;
        }

        boolean servicesWorking = !"NONE".equals(live.source());
        if (servicesWorking && radiusMeters < EXPANDED_RADIUS) {
            // Nothing that close: show the nearest gyms a bit further away rather than an empty map.
            GymSearchResponse wider = searchWithCache(lat, lng, EXPANDED_RADIUS);
            if (!wider.gyms().isEmpty()) {
                List<Gym> nearest = wider.gyms().stream().limit(EXPANDED_SHOWN).toList();
                return new GymSearchResponse(wider.source(),
                        "No gyms found within " + km(radiusMeters) + ", so here are the nearest ones within "
                                + km(EXPANDED_RADIUS) + ".", nearest);
            }
        }

        if (!servicesWorking) {
            Optional<GymSearchResponse> saved = savedResults(lat, lng, radiusMeters);
            if (saved.isPresent()) {
                return saved.get();
            }
        }
        return live;
    }

    // ===================== background warm-up =====================

    /**
     * Runs once the app has started: searches the default city (Nagpur) in a background thread,
     * unless fresh saved results already exist, so the gym page is instant and has a saved copy.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpDefaultCity() {
        if (!warmUpEnabled) {
            return;
        }
        Optional<GymCacheStore.SavedSearch> saved = cacheStore.get(cacheKey(defaultLat, defaultLng, EXPANDED_RADIUS));
        if (saved.isPresent() && saved.get().savedAt().plus(WARM_UP_MAX_AGE).isAfter(Instant.now())) {
            return;
        }
        Thread thread = new Thread(() -> {
            try {
                GymSearchResponse result = findNearby(defaultLat, defaultLng, EXPANDED_RADIUS);
                log.info("Gym warm-up for the default city: {} gyms from {}", result.gyms().size(), result.source());
            } catch (RuntimeException e) {
                log.warn("Gym warm-up failed: {}", e.getMessage());
            }
        }, "gym-warm-up");
        thread.setDaemon(true);
        thread.start();
    }

    // ===================== providers =====================

    /** One radius: memory cache, else the live providers. Non-empty live results are also saved to disk. */
    private GymSearchResponse searchWithCache(double lat, double lng, int radius) {
        String key = cacheKey(lat, lng, radius);
        CacheEntry cached = memoryCache.get(key);
        if (cached != null && cached.createdAt().plus(MEMORY_CACHE_TTL).isAfter(Instant.now())) {
            return cached.response();
        }

        GymSearchResponse live = searchLive(lat, lng, radius);
        if (!"NONE".equals(live.source())) {
            memoryCache.put(key, new CacheEntry(live, Instant.now()));
        }
        if (!live.gyms().isEmpty()) {
            cacheStore.save(key, new GymCacheStore.SavedSearch(live.source(), Instant.now(), live.gyms()));
        }
        return live;
    }

    private GymSearchResponse searchLive(double lat, double lng, int radius) {
        String note = null;
        if (googlePlacesClient.isConfigured()) {
            try {
                return new GymSearchResponse("GOOGLE_PLACES", null, normalize(googlePlacesClient.searchGyms(lat, lng, radius), lat, lng, radius));
            } catch (RuntimeException e) {
                log.warn("Google Places failed, falling back to OpenStreetMap: {}", e.getMessage());
                note = "Google Places was unavailable, showing OpenStreetMap results.";
            }
        }

        if (Instant.now().isBefore(skipOverpassUntil)) {
            log.info("Skipping Overpass (it failed recently), using Nominatim");
        } else {
            try {
                List<Gym> gyms = normalize(overpassClient.searchGyms(lat, lng, radius), lat, lng, radius);
                skipOverpassUntil = Instant.MIN; // it works again
                return new GymSearchResponse("OPENSTREETMAP", note, gyms);
            } catch (RuntimeException e) {
                skipOverpassUntil = Instant.now().plus(OVERPASS_COOL_DOWN);
                log.warn("OpenStreetMap Overpass failed, trying Nominatim (Overpass skipped for {} min): {}",
                        OVERPASS_COOL_DOWN.toMinutes(), e.getMessage());
            }
        }

        try {
            List<Gym> gyms = normalize(nominatimClient.searchGyms(lat, lng, radius), lat, lng, radius);
            return new GymSearchResponse("OPENSTREETMAP",
                    "The main OpenStreetMap gym search is busy, so these results come from a name search and may be incomplete.",
                    gyms);
        } catch (RuntimeException e) {
            log.warn("OpenStreetMap Nominatim failed: {}", e.getMessage());
            return new GymSearchResponse("NONE",
                    "Gym search is unavailable right now (no internet connection or the map services are busy). Please try again later.",
                    List.of());
        }
    }

    /**
     * Every live provider failed: use the last saved results for this spot. An exact radius match
     * is best; otherwise a saved 10 km search is cut down to the requested radius (or, if that
     * leaves nothing, its nearest gyms).
     */
    private Optional<GymSearchResponse> savedResults(double lat, double lng, int radius) {
        Optional<GymCacheStore.SavedSearch> exact = cacheStore.get(cacheKey(lat, lng, radius));
        if (exact.isPresent()) {
            return Optional.of(savedResponse(exact.get(), exact.get().gyms(), null));
        }
        Optional<GymCacheStore.SavedSearch> wide = cacheStore.get(cacheKey(lat, lng, EXPANDED_RADIUS));
        if (wide.isEmpty() || radius > EXPANDED_RADIUS) {
            return Optional.empty();
        }
        double maxKm = radius / 1000.0;
        List<Gym> within = wide.get().gyms().stream().filter(g -> g.distanceKm() <= maxKm).toList();
        if (!within.isEmpty()) {
            return Optional.of(savedResponse(wide.get(), within, null));
        }
        return Optional.of(savedResponse(wide.get(), wide.get().gyms().stream().limit(EXPANDED_SHOWN).toList(),
                " None were within " + km(radius) + ", so these are the nearest ones."));
    }

    private GymSearchResponse savedResponse(GymCacheStore.SavedSearch saved, List<Gym> gyms, String extra) {
        String message = "Live gym search is unavailable right now, so these are saved results from "
                + SAVED_AT_FORMAT.format(saved.savedAt()) + "." + (extra == null ? "" : extra);
        return new GymSearchResponse("SAVED", message, gyms);
    }

    // ===================== helpers =====================

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

    /** ~100 m precision (3 decimals) is plenty for a cache key. */
    static String cacheKey(double lat, double lng, int radius) {
        return String.format(Locale.ROOT, "%.3f,%.3f,%d", lat, lng, radius);
    }

    private static String km(int metres) {
        return (metres % 1000 == 0 ? String.valueOf(metres / 1000) : String.valueOf(metres / 1000.0)) + " km";
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
