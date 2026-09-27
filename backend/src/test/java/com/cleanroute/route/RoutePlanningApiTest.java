package com.cleanroute.route;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:phase6-route-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class RoutePlanningApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    private String register(String email) throws Exception {
        MvcResult response = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-battery\",\"displayName\":\"Route User\"}"))
                .andExpect(status().isCreated()).andReturn();
        return mapper.readTree(response.getResponse().getContentAsString()).get("token").asText();
    }

    @Test void routeCalculationRequiresAuthReturnsRankedAlternativesAndRetrievesOnlyForOwner() throws Exception {
        String body = "{\"origin\":{\"latitude\":28.6139,\"longitude\":77.2090},"
                + "\"destination\":{\"latitude\":28.7041,\"longitude\":77.1025},"
                + "\"mode\":\"WALK\",\"preference\":\"BALANCED\"}";
        mvc.perform(post("/api/routes/calculate").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        String ownerToken = register("route-owner@example.test");
        String otherToken = register("route-other@example.test");
        MvcResult result = mvc.perform(post("/api/routes/calculate").header("Authorization", "Bearer " + ownerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.alternatives.length()").value(3))
                .andExpect(jsonPath("$.alternatives[0].rank").value(1))
                .andExpect(jsonPath("$.alternatives[0].geometry.length()").isNumber())
                .andExpect(jsonPath("$.alternatives[0].expectedPollutionExposure").isNumber())
                .andExpect(jsonPath("$.alternatives[0].scoreComponents").exists())
                .andExpect(jsonPath("$.alternatives[0].reasons").isArray())
                .andExpect(jsonPath("$.generated").value(true))
                .andReturn();
        JsonNode calculated = mapper.readTree(result.getResponse().getContentAsString());
        String id = calculated.get("id").asText();
        mvc.perform(get("/api/routes/" + id).header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/routes/" + id).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }

    @Test void routeCalculationValidatesCoordinatesAndRejectsLaterPhasePreferences() throws Exception {
        String token = register("route-invalid@example.test");
        String invalidCoordinates = "{\"origin\":{\"latitude\":91,\"longitude\":77},"
                + "\"destination\":{\"latitude\":28,\"longitude\":77},\"mode\":\"WALK\",\"preference\":\"FASTEST\"}";
        mvc.perform(post("/api/routes/calculate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(invalidCoordinates))
                .andExpect(status().isBadRequest());
        String laterPreference = "{\"origin\":{\"latitude\":28,\"longitude\":77},"
                + "\"destination\":{\"latitude\":29,\"longitude\":78},\"mode\":\"CYCLE\",\"preference\":\"CYCLIST\"}";
        mvc.perform(post("/api/routes/calculate").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(laterPreference))
                .andExpect(status().isBadRequest());
    }
}
