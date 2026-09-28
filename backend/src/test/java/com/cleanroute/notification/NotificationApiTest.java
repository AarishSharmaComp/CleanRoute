package com.cleanroute.notification;

import com.cleanroute.notification.service.NotificationService;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationResult;
import com.cleanroute.route.domain.RoutePlanningModels.RouteAlternative;
import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:phase9-notifications;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class NotificationApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired NotificationService notifications;
    @Autowired JdbcTemplate jdbc;

    private String register(String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\",\"displayName\":\"Notify User\"}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    @Test void generatedAlertsAreDeduplicatedPrivateAndOwnerReadOnly() throws Exception {
        String ownerToken = register("notification-owner@example.test");
        String otherToken = register("notification-other@example.test");
        mvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        String userBody = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID owner = UUID.fromString(mapper.readTree(userBody).get("id").asText());

        Instant target = Instant.now().plusSeconds(900);
        var forecast = new PollutionForecast("demo-delhi-central", target, Instant.now(), 180, 60.0, null,
                null, null, null, null, 40, "LOW", 2, "historical-average", "test-v1", true);
        notifications.checkForecast(owner, forecast);
        notifications.checkForecast(owner, forecast);
        var first = new RouteAlternative("fast", 1, "mock", true, List.of(), 1000, 600, 40,
                30, 70, Map.of("expectedPollutionExposure", 40.0), List.of("Fastest"));
        var cleaner = new RouteAlternative("cleaner", 2, "mock", true, List.of(), 1200, 700, 20,
                30, 65, Map.of("expectedPollutionExposure", 20.0), List.of("Cleaner"));
        notifications.checkCleanerAlternative(owner, new CalculationResult(UUID.randomUUID(), target,
                TravelMode.WALK, RoutePreference.FASTEST, List.of(first, cleaner), true, "Demo only"));

        MvcResult listResult = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andReturn();
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(0)));
        String notificationId = mapper.readTree(listResult.getResponse().getContentAsString()).get(0).get("id").asText();
        mvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").exists());
        mvc.perform(post("/api/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cellId").value("demo-delhi-central"))
                .andExpect(jsonPath("$.observations").isArray()).andExpect(jsonPath("$.forecasts").isArray())
                .andExpect(jsonPath("$.savedPlaces").isArray()).andExpect(jsonPath("$.routeHistory").isArray())
                .andExpect(jsonPath("$.unreadNotificationCount").isNumber());
    }

    @Test void ownerCanMarkNotificationOutsideNewestHundredReadWithoutCrossOwnerAccess() throws Exception {
        String ownerToken = register("notification-old-owner@example.test");
        String otherToken = register("notification-old-other@example.test");
        String userBody = mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID ownerId = UUID.fromString(mapper.readTree(userBody).get("id").asText());

        UUID oldNotificationId = UUID.randomUUID();
        insertNotification(oldNotificationId, ownerId, "old-notification", Instant.now().minusSeconds(30L * 24 * 60 * 60));
        for (int index = 0; index < 101; index++) {
            insertNotification(UUID.randomUUID(), ownerId, "recent-notification-" + index,
                    Instant.now().minusSeconds(101 - index));
        }

        MvcResult recentList = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(50)))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(recentList.getResponse().getContentAsString())
                .doesNotContain(oldNotificationId.toString());

        mvc.perform(post("/api/notifications/" + oldNotificationId + "/read")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(oldNotificationId.toString()))
                .andExpect(jsonPath("$.readAt").isNotEmpty());
        mvc.perform(post("/api/notifications/" + oldNotificationId + "/read")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isNotFound());
    }

    private void insertNotification(UUID id, UUID ownerId, String dedupKey, Instant createdAt) {
        jdbc.update("INSERT INTO user_notification(id,user_id,kind,dedup_key,title,message,created_at) VALUES (?,?,?,?,?,?,?)",
                id, ownerId, "HIGH_POLLUTION_FORECAST", dedupKey, "Test alert", "Test notification",
                Timestamp.from(createdAt));
    }
}
