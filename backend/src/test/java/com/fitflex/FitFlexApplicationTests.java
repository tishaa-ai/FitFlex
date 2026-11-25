package com.fitflex;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test: the whole Spring application context (entities, repositories, config) starts.
 * Uses the in-memory H2 profile so no MySQL is needed.
 */
@SpringBootTest(properties = {"fitflex.gym.warm-up=false", "fitflex.gym.cache-file=target/test-gym-cache.json"})
@ActiveProfiles("h2")
class FitFlexApplicationTests {

    @Test
    void contextLoads() {
    }
}
