package com.fitflex.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Small pure functions that protect security or correctness: upload file-name sanitising,
 * the haversine distance used by the gym locator, and the diet compatibility rules.
 */
class HelperLogicTest {

    @Test
    void sanitizeRemovesFoldersAndDangerousCharacters() {
        assertThat(ResourceService.sanitizeFileName("../../etc/passwd.pdf")).isEqualTo("passwd.pdf");
        assertThat(ResourceService.sanitizeFileName("C:\\fakepath\\My Plan (v1).pdf")).isEqualTo("My Plan _v1_.pdf");
        assertThat(ResourceService.sanitizeFileName("<script>.png")).isEqualTo("_script_.png");
    }

    @Test
    void sanitizeHandlesHiddenEmptyAndVeryLongNames() {
        assertThat(ResourceService.sanitizeFileName(".htaccess")).isEqualTo("htaccess");
        assertThat(ResourceService.sanitizeFileName(null)).isEqualTo("file");
        String longName = "a".repeat(300) + ".pdf";
        String safe = ResourceService.sanitizeFileName(longName);
        assertThat(safe).hasSizeLessThanOrEqualTo(120).endsWith(".pdf");
    }

    @Test
    void haversineDistanceBetweenNagpurAndMumbai() {
        // Straight-line distance is about 685 km.
        double km = GymService.distanceKm(21.1458, 79.0882, 19.0760, 72.8777);
        assertThat(km).isCloseTo(685, within(10.0));
    }

    @Test
    void haversineDistanceToSamePointIsZero() {
        assertThat(GymService.distanceKm(21.1458, 79.0882, 21.1458, 79.0882)).isEqualTo(0.0);
    }

    @Test
    void dietCompatibilityFollowsStrictness() {
        assertThat(DietType.VEGETARIAN.allows(DietType.VEGAN)).isTrue();      // vegan food is fine for vegetarians
        assertThat(DietType.VEGETARIAN.allows(DietType.EGGETARIAN)).isFalse(); // eggs are not
        assertThat(DietType.VEGAN.allows(DietType.VEGETARIAN)).isFalse();     // dairy is not vegan
        assertThat(DietType.NON_VEG.allows(DietType.VEGAN)).isTrue();
    }
}
