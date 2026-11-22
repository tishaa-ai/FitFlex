package com.fitflex.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Free, key-less gym search using OpenStreetMap data through the Overpass API.
 * Asks for every map feature tagged leisure=fitness_centre within the radius.
 * The public Overpass servers are sometimes overloaded; GymService then tries Nominatim.
 */
@Component
public class OverpassClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String url;

    public OverpassClient(RestClient externalApiClient, ObjectMapper objectMapper,
                          @Value("${fitflex.overpass.url:https://overpass-api.de/api/interpreter}") String url) {
        this.restClient = externalApiClient;
        this.objectMapper = objectMapper;
        this.url = url;
    }

    public List<GooglePlacesClient.Place> searchGyms(double lat, double lng, int radiusMeters) {
        // Overpass QL: nodes, ways (buildings) and relations; "out center" gives ways a centre point.
        // timeout:10 asks the server to give up after 10 s, so we fall back quickly when it is busy.
        String query = String.format(Locale.ROOT, """
                [out:json][timeout:10];
                (
                  nwr["leisure"="fitness_centre"](around:%d,%f,%f);
                );
                out center 40;
                """, radiusMeters, lat, lng);
        String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        String json = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .accept(MediaType.APPLICATION_JSON)
                .body(form)
                .retrieve()
                .body(String.class);
        return parse(objectMapper.readTree(json));
    }

    private List<GooglePlacesClient.Place> parse(JsonNode root) {
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
}
