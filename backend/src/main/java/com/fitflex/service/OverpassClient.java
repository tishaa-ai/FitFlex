package com.fitflex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Free, key-less gym search using OpenStreetMap data through the Overpass API: every map feature
 * tagged leisure=fitness_centre within the radius.
 *
 * The public Overpass servers are often overloaded, so to be reliable this client:
 * 1. asks SEVERAL mirror servers at the same time and uses the first good answer
 *    ({@link ExecutorService#invokeAny}: "run all, return the first that succeeds, cancel the rest");
 * 2. retries a server once when it says it is busy (HTTP 429/502/503/504) or times out;
 * 3. treats a "200 OK" whose body contains a runtime error ("query timed out", "out of memory")
 *    as a failure, because Overpass reports overload that way too;
 * 4. gives up after an overall deadline, so GymService can fall back to Nominatim.
 */
@Component
public class OverpassClient {

    private static final Logger log = LoggerFactory.getLogger(OverpassClient.class);
    private static final Set<Integer> BUSY_STATUSES = Set.of(429, 502, 503, 504);
    private static final int ATTEMPTS_PER_SERVER = 2;
    private static final long RETRY_PAUSE_MS = 2000;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final List<String> serverUrls;
    private final long deadlineSeconds;

    /** Daemon threads: a server still hanging when the app shuts down won't keep the JVM alive. */
    private final ExecutorService pool = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "overpass-query");
        thread.setDaemon(true);
        return thread;
    });

    public OverpassClient(@Qualifier("overpassRestClient") RestClient restClient, ObjectMapper objectMapper,
                          @Value("${fitflex.overpass.urls}") String urls,
                          @Value("${fitflex.overpass.deadline-seconds:30}") long deadlineSeconds) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.serverUrls = Arrays.stream(urls.split(",")).map(String::trim).filter(u -> !u.isEmpty()).toList();
        this.deadlineSeconds = deadlineSeconds;
    }

    public List<GooglePlacesClient.Place> searchGyms(double lat, double lng, int radiusMeters) {
        // Overpass QL: nodes, ways (buildings) and relations; "out center" gives ways a centre point.
        // timeout:25 lets a busy server take up to 25 s; we keep only the nearest 30 later.
        String query = String.format(Locale.ROOT, """
                [out:json][timeout:25];
                (
                  nwr["leisure"="fitness_centre"](around:%d,%f,%f);
                );
                out center 100;
                """, radiusMeters, lat, lng);
        String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        List<Callable<List<GooglePlacesClient.Place>>> tasks = serverUrls.stream()
                .<Callable<List<GooglePlacesClient.Place>>>map(url -> () -> queryWithRetry(url, form))
                .toList();
        try {
            // Returns the result of the first server that answers successfully.
            return pool.invokeAny(tasks, deadlineSeconds, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            throw new IllegalStateException("All " + serverUrls.size() + " Overpass servers failed (last error: "
                    + e.getCause().getMessage() + ")", e);
        } catch (TimeoutException e) {
            throw new IllegalStateException("No Overpass server answered within " + deadlineSeconds + " s", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for Overpass", e);
        }
    }

    /** One server: up to two attempts, pausing briefly when the server says it is busy. */
    private List<GooglePlacesClient.Place> queryWithRetry(String url, String form) throws InterruptedException {
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= ATTEMPTS_PER_SERVER; attempt++) {
            try {
                String json = restClient.post()
                        .uri(url)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(form)
                        .retrieve()
                        .body(String.class);
                return parse(objectMapper.readTree(json));
            } catch (RestClientResponseException e) {
                if (!BUSY_STATUSES.contains(e.getStatusCode().value())) {
                    throw e; // e.g. 400 Bad Request: retrying would not help
                }
                lastError = e;
            } catch (ResourceAccessException | OverpassBusyException e) {
                lastError = e; // timeout / connection problem / overloaded server
            } catch (RestClientException e) {
                lastError = e; // the body could not be read (connection dropped mid-answer)
            }
            log.debug("Overpass {} attempt {} failed: {}", url, attempt, lastError.getMessage());
            if (attempt < ATTEMPTS_PER_SERVER) {
                Thread.sleep(RETRY_PAUSE_MS * attempt);
            }
        }
        throw lastError;
    }

    private List<GooglePlacesClient.Place> parse(JsonNode root) {
        // When overloaded, Overpass may answer 200 OK with no elements and a "remark" describing the error.
        String remark = root.path("remark").asString("");
        if (remark.toLowerCase(Locale.ROOT).contains("error")) {
            throw new OverpassBusyException(remark);
        }
        if (!root.has("elements")) {
            throw new OverpassBusyException("Unexpected answer without 'elements'");
        }

        List<GooglePlacesClient.Place> places = new ArrayList<>();
        for (JsonNode el : root.path("elements")) {
            // Nodes have lat/lon directly; ways and relations have them under "center".
            JsonNode point = el.has("lat") ? el : el.path("center");
            if (!point.has("lat")) {
                continue;
            }
            JsonNode tags = el.path("tags");
            String name = tags.path("name").asString("");
            if (name.isBlank()) {
                name = tags.path("brand").asString("Fitness Centre");
            }
            places.add(new GooglePlacesClient.Place(name, buildAddress(tags),
                    point.path("lat").asDouble(), point.path("lon").asDouble(), null));
        }
        return places;
    }

    /** OSM stores the address in parts (addr:housenumber, addr:street ...); join the ones present. */
    private String buildAddress(JsonNode tags) {
        List<String> parts = new ArrayList<>();
        String street = (tags.path("addr:housenumber").asString("") + " " + tags.path("addr:street").asString("")).trim();
        if (!street.isEmpty()) parts.add(street);
        for (String key : List.of("addr:suburb", "addr:city", "addr:postcode")) {
            String value = tags.path(key).asString("");
            if (!value.isBlank()) parts.add(value);
        }
        return parts.isEmpty() ? "Address not listed on OpenStreetMap" : String.join(", ", parts);
    }

    /** Overpass answered, but the answer says it was too busy to run the query. */
    static class OverpassBusyException extends RuntimeException {
        OverpassBusyException(String message) {
            super(message);
        }
    }
}
