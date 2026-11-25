package com.fitflex.service;

import com.fitflex.dto.response.GymSearchResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests GymService's fallback chain with mocked map providers and a real GymCacheStore
 * writing to a temporary folder.
 */
@ExtendWith(MockitoExtension.class)
class GymServiceTest {

    private static final double LAT = 21.1458;   // Nagpur
    private static final double LNG = 79.0882;

    @Mock
    private GooglePlacesClient googlePlacesClient;   // isConfigured() is false by default -> skipped
    @Mock
    private OverpassClient overpassClient;
    @Mock
    private NominatimClient nominatimClient;

    @TempDir
    Path tempDir;

    private GymCacheStore cacheStore;
    private GymService gymService;

    /** 0.009 degrees of latitude is about 1 km. */
    private static GooglePlacesClient.Place gymKmNorth(String name, double km) {
        return new GooglePlacesClient.Place(name, "Nagpur", LAT + km * 0.009, LNG, null);
    }

    private GymService newService(GymCacheStore store) {
        return new GymService(googlePlacesClient, overpassClient, nominatimClient, store, false, LAT, LNG);
    }

    @BeforeEach
    void setUp() {
        cacheStore = new GymCacheStore(tempDir.resolve("gym-cache.json").toString(), JsonMapper.builder().build());
        gymService = newService(cacheStore);
    }

    @Test
    void overpassResultsAreFilteredAndSortedByDistance() {
        when(overpassClient.searchGyms(LAT, LNG, 3000)).thenReturn(List.of(
                gymKmNorth("Far Gym", 2.5), gymKmNorth("Near Gym", 0.5), gymKmNorth("Too Far", 4.0)));

        GymSearchResponse result = gymService.findNearby(LAT, LNG, 3000);

        assertThat(result.source()).isEqualTo("OPENSTREETMAP");
        assertThat(result.gyms()).extracting(GymSearchResponse.Gym::name).containsExactly("Near Gym", "Far Gym");
        verify(nominatimClient, never()).searchGyms(anyDouble(), anyDouble(), anyInt());
    }

    @Test
    void fallsBackToNominatimAndThenSkipsOverpassForAWhile() {
        when(overpassClient.searchGyms(anyDouble(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("all busy"));
        when(nominatimClient.searchGyms(anyDouble(), anyDouble(), anyInt())).thenReturn(List.of(gymKmNorth("Named Gym", 1)));

        GymSearchResponse first = gymService.findNearby(LAT, LNG, 3000);
        GymSearchResponse second = gymService.findNearby(LAT, LNG, 5000);   // different radius, no cache hit

        assertThat(first.gyms()).hasSize(1);
        assertThat(first.message()).contains("name search");
        assertThat(second.gyms()).hasSize(1);
        verify(overpassClient, times(1)).searchGyms(anyDouble(), anyDouble(), anyInt()); // circuit breaker
    }

    @Test
    void nothingInRadiusShowsNearestWithinTenKm() {
        when(overpassClient.searchGyms(LAT, LNG, 3000)).thenReturn(List.of());
        when(overpassClient.searchGyms(LAT, LNG, GymService.EXPANDED_RADIUS))
                .thenReturn(List.of(gymKmNorth("Gym at 3.2 km", 3.2), gymKmNorth("Gym at 6 km", 6)));

        GymSearchResponse result = gymService.findNearby(LAT, LNG, 3000);

        assertThat(result.gyms()).extracting(GymSearchResponse.Gym::name).containsExactly("Gym at 3.2 km", "Gym at 6 km");
        assertThat(result.message()).contains("No gyms found within 3 km").contains("within 10 km");
    }

    @Test
    void savedResultsAreUsedWhenEveryProviderFails() {
        // 1) Services are up: a 10 km search is saved to disk.
        when(overpassClient.searchGyms(LAT, LNG, GymService.EXPANDED_RADIUS))
                .thenReturn(List.of(gymKmNorth("Close Gym", 1.5), gymKmNorth("Distant Gym", 8)));
        gymService.findNearby(LAT, LNG, GymService.EXPANDED_RADIUS);
        assertThat(Files.exists(tempDir.resolve("gym-cache.json"))).isTrue();

        // 2) "Restart" with a new service + store reading the same file; now everything fails.
        GymService afterRestart = newService(new GymCacheStore(tempDir.resolve("gym-cache.json").toString(), JsonMapper.builder().build()));
        when(overpassClient.searchGyms(eq(LAT), eq(LNG), eq(3000))).thenThrow(new IllegalStateException("down"));
        when(nominatimClient.searchGyms(anyDouble(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("offline"));

        GymSearchResponse result = afterRestart.findNearby(LAT, LNG, 3000);

        assertThat(result.source()).isEqualTo("SAVED");
        assertThat(result.message()).contains("saved results from");
        assertThat(result.gyms()).extracting(GymSearchResponse.Gym::name).containsExactly("Close Gym"); // cut to 3 km
    }

    @Test
    void nothingSavedAndEverythingDownGivesClearMessage() {
        when(overpassClient.searchGyms(anyDouble(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("down"));
        when(nominatimClient.searchGyms(anyDouble(), anyDouble(), anyInt())).thenThrow(new IllegalStateException("offline"));

        GymSearchResponse result = gymService.findNearby(LAT, LNG, 3000);

        assertThat(result.source()).isEqualTo("NONE");
        assertThat(result.gyms()).isEmpty();
        assertThat(result.message()).contains("unavailable");
    }

    @Test
    void repeatedSearchIsServedFromMemory() {
        when(overpassClient.searchGyms(LAT, LNG, 3000)).thenReturn(List.of(gymKmNorth("Near Gym", 0.5)));

        gymService.findNearby(LAT, LNG, 3000);
        gymService.findNearby(21.14581, 79.08819, 3000); // same spot within ~100 m

        verify(overpassClient, times(1)).searchGyms(anyDouble(), anyDouble(), anyInt());
    }
}
