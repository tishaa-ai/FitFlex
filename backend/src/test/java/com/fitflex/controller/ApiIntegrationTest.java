package com.fitflex.controller;

import com.fitflex.entity.SessionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests: the whole Spring application (security filter chain, controllers, services,
 * JPA) runs against the in-memory H2 database with the demo seed data. MockMvc sends fake HTTP
 * requests without starting a real server.
 */
@SpringBootTest(properties = {"fitflex.upload.dir=target/test-uploads",
        "fitflex.gym.warm-up=false", "fitflex.gym.cache-file=target/test-gym-cache.json"})
@AutoConfigureMockMvc
@ActiveProfiles("h2")
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private String userJson(String email) {
        return """
                {"fullName":"Test User","email":"%s","password":"Passw0rd!","age":28,"gender":"FEMALE",
                 "heightCm":162,"weightKg":58,"fitnessGoal":"MAINTENANCE","fitnessLevel":"BEGINNER","activityLevel":"LIGHT"}
                """.formatted(email);
    }

    /** Logs in and returns the JWT. */
    private String login(String path, String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).path("token").asString();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // ===================== register / login =====================

    @Test
    void registerThenLogin() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON).content(userJson(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role").value("USER"))
                .andExpect(jsonPath("$.name").value("Test User"));

        String token = login("/api/user/login", email, "Passw0rd!");
        assertThat(token.split("\\.")).hasSize(3); // header.payload.signature
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON).content(userJson(email)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON).content(userJson(email.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message", containsString("already exists")));
    }

    @Test
    void registerWithInvalidDataReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/user/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.email").value("Enter a valid email address"))
                .andExpect(jsonPath("$.errors.password", containsString("8 characters")))
                .andExpect(jsonPath("$.errors.fullName", notNullValue()));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@fitflex.com\",\"password\":\"Wrong1234\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void trainerRegistersAndLogsInThroughTrainerPortalOnly() throws Exception {
        String email = uniqueEmail();
        mockMvc.perform(post("/api/trainer/add").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"New Coach","email":"%s","password":"Coach1234","specialization":"Cardio",
                         "experienceYears":3,"pricePerSession":450}""".formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("TRAINER"))
                .andExpect(jsonPath("$.trainerId", notNullValue()));

        login("/api/trainer/login", email, "Coach1234");
        mockMvc.perform(post("/api/user/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"Coach1234\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("trainer login")));
    }

    // ===================== public trainer directory =====================

    @Test
    void trainersListIsPublicAndFilterable() throws Exception {
        mockMvc.perform(get("/api/trainers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName", notNullValue()))
                .andExpect(jsonPath("$[0].email").doesNotExist()); // no contact details in public listings

        mockMvc.perform(get("/api/trainers").param("specialization", "yoga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fullName").value("Priya Sharma"));

        mockMvc.perform(get("/api/trainers").param("maxPrice", "600"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].pricePerSession").value(org.hamcrest.Matchers.everyItem(
                        org.hamcrest.Matchers.lessThanOrEqualTo(600.0))));
    }

    @Test
    void unknownTrainerReturns404() throws Exception {
        mockMvc.perform(get("/api/trainers/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Trainer not found"));
    }

    // ===================== security =====================

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/user/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        mockMvc.perform(get("/api/progress/summary")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointWithTokenWorks() throws Exception {
        String token = login("/api/user/login", "user@fitflex.com", "User@1234");
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@fitflex.com"))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void wrongRoleReturns403() throws Exception {
        String trainerToken = login("/api/trainer/login", "trainer1@fitflex.com", "Trainer@1234");
        mockMvc.perform(get("/api/user/profile").header("Authorization", "Bearer " + trainerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/diet/generate").header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dietType\":\"vegan\"}"))
                .andExpect(status().isForbidden());
    }

    // ===================== a full flow through several layers =====================

    @Test
    void bookConfirmAndGetJitsiLink() throws Exception {
        String userToken = login("/api/user/login", "user@fitflex.com", "User@1234");
        String trainerToken = login("/api/trainer/login", "trainer1@fitflex.com", "Trainer@1234");
        // Arjun (trainer 1) works Mondays 07:00-11:00. Use a Monday 3+ weeks away at 10:00 to avoid seed sessions.
        String when = LocalDate.now().plusWeeks(3).with(TemporalAdjusters.next(DayOfWeek.MONDAY)) + "T10:00";

        MvcResult booked = mockMvc.perform(post("/api/sessions/book").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerId\":1,\"scheduledAt\":\"%s\",\"durationMinutes\":60}".formatted(when)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andReturn();
        long sessionId = json(booked).path("id").asLong();

        // Same slot again -> clash
        mockMvc.perform(post("/api/sessions/book").header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"trainerId\":1,\"scheduledAt\":\"%s\"}".formatted(when)))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/sessions/" + sessionId + "/status").header("Authorization", "Bearer " + trainerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + SessionStatus.CONFIRMED + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meetingLink", startsWith("https://meet.jit.si/FitFlex-")));
    }

    @Test
    void dietPlanGeneratesWithLocalFallback() throws Exception {
        String token = login("/api/user/login", "user@fitflex.com", "User@1234");
        mockMvc.perform(post("/api/diet/generate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"goal\":\"WEIGHT_LOSS\",\"dietType\":\"vegetarian\",\"mealsPerDay\":3}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("LOCAL_FALLBACK"))
                .andExpect(jsonPath("$.mealGroups", hasSize(3)))
                .andExpect(jsonPath("$.targetCalories", notNullValue()));
    }
}
