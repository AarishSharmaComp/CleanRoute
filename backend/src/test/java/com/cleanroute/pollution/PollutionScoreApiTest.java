package com.cleanroute.pollution;

import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.repository.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:phase4-score-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class PollutionScoreApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObservationRepository observations;

    @Test void rejectsMissingRequiredQueryParametersAsClientErrors() throws Exception {
        mvc.perform(get("/api/pollution/score"))
                .andExpect(status().isBadRequest());

        Instant at = observations.latest("demo-delhi-central").getFirst().observedAt();
        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central")
                        .param("at", at.toString()).param("durationSeconds", "600")
                        .param("distanceMeters", "800"))
                .andExpect(status().isBadRequest());
    }

    @Test void publicApiScoresStoredIntervalAndExplainsGeneratedInputs() throws Exception {
        Instant at = observations.latest("demo-delhi-central").getFirst().observedAt();
        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central")
                        .param("at", at.toString()).param("durationSeconds", "1800")
                        .param("distanceMeters", "2400").param("mode", "WALK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cellId").value("demo-delhi-central"))
                .andExpect(jsonPath("$.observedAt").value(at.toString()))
                .andExpect(jsonPath("$.score").isNumber())
                .andExpect(jsonPath("$.direction").value("Higher means greater modeled comparative burden; this is not a route ranking."))
                .andExpect(jsonPath("$.generated").value(true))
                .andExpect(jsonPath("$.pollutionProvider").value("mock-demo-aqi"))
                .andExpect(jsonPath("$.components.length()").value(3))
                .andExpect(jsonPath("$.pollutantCoveragePercent").value(67))
                .andExpect(jsonPath("$.missingPollutants").isArray())
                .andExpect(jsonPath("$.pollutants[3].pollutant").value("SO2"))
                .andExpect(jsonPath("$.pollutants[3].measuredValue").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.pollutants[3].score").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.pollutants[3].includedInBurden").value(false))
                .andExpect(jsonPath("$.caveats.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(3)));
    }

    @Test void rejectsUnknownCellsMissingPollutionAndUnalignedOrFutureIntervals() throws Exception {
        Instant aligned = Instant.ofEpochSecond(Math.floorDiv(Instant.now().getEpochSecond(), 900) * 900);
        mvc.perform(get("/api/pollution/score").param("cell", "unknown-cell").param("at", aligned.toString())
                        .param("durationSeconds", "600").param("distanceMeters", "800").param("mode", "WALK"))
                .andExpect(status().isNotFound());

        String emptyCell = "phase4-empty-observation-cell";
        Instant emptyInterval = Instant.ofEpochSecond(Math.floorDiv(Instant.now().getEpochSecond(), 900) * 900);
        observations.saveCell(new GeographicCell(emptyCell, 28.6, 77.2));
        mvc.perform(get("/api/pollution/score").param("cell", emptyCell)
                        .param("at", emptyInterval.toString()).param("durationSeconds", "600")
                        .param("distanceMeters", "800").param("mode", "WALK"))
                .andExpect(status().isNotFound());

        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central")
                        .param("at", aligned.plusSeconds(60).toString()).param("durationSeconds", "600")
                        .param("distanceMeters", "800").param("mode", "WALK"))
                .andExpect(status().isBadRequest());

        Instant future = Instant.ofEpochSecond(Math.floorDiv(Instant.now().plusSeconds(3600).getEpochSecond(), 900) * 900);
        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central")
                        .param("at", future.toString()).param("durationSeconds", "600")
                        .param("distanceMeters", "800").param("mode", "WALK"))
                .andExpect(status().isBadRequest());
    }

    @Test void missingPollutantsAndAqiReturnUnprocessableRatherThanFabricatedScore() throws Exception {
        String cell = "phase4-no-measurements";
        Instant at = Instant.ofEpochSecond(Math.floorDiv(Instant.now().minusSeconds(3600).getEpochSecond(), 900) * 900);
        observations.saveCell(new GeographicCell(cell, 28.6, 77.2));
        observations.save(new PollutionObservation(cell, at, null, null, null, null,
                null, null, null, "empty-provider", true));
        mvc.perform(get("/api/pollution/score").param("cell", cell).param("at", at.toString())
                        .param("durationSeconds", "600").param("distanceMeters", "800").param("mode", "WALK"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test void validatesDurationDistanceAndTravelMode() throws Exception {
        Instant at = observations.latest("demo-delhi-central").getFirst().observedAt();
        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central").param("at", at.toString())
                        .param("durationSeconds", "0").param("distanceMeters", "800").param("mode", "WALK"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central").param("at", at.toString())
                        .param("durationSeconds", "600").param("distanceMeters", "0").param("mode", "WALK"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/score").param("cell", "demo-delhi-central").param("at", at.toString())
                        .param("durationSeconds", "600").param("distanceMeters", "800").param("mode", "FLY"))
                .andExpect(status().isBadRequest());
    }
}
