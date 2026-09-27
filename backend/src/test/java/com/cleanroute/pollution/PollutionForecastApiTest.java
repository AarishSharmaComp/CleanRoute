package com.cleanroute.pollution;

import com.cleanroute.observation.repository.ObservationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:phase5-forecast-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class PollutionForecastApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObservationRepository observations;

    @Test void forecastIsPersistedAndExplicitlySeparatedFromObservedData() throws Exception {
        long epoch = Instant.now().getEpochSecond();
        Instant from = Instant.ofEpochSecond(Math.floorDiv(epoch, 900) * 900 + 900);
        mvc.perform(get("/api/pollution/forecast").param("cell", "demo-delhi-central")
                        .param("from", from.toString()).param("interval", "15").param("count", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].dataType").value("PREDICTED"))
                .andExpect(jsonPath("$[0].observed").value(false)).andExpect(jsonPath("$[0].predicted").value(true))
                .andExpect(jsonPath("$[0].forecast.targetAt").exists())
                .andExpect(jsonPath("$[0].forecast.qualityScore").isNumber())
                .andExpect(jsonPath("$[0].forecast.pm25").isNumber())
                .andExpect(jsonPath("$[0].forecast.provider").value("historical-average"))
                .andExpect(jsonPath("$[0].limitation").exists());
        mvc.perform(get("/api/pollution/forecast/history").param("cell", "demo-delhi-central")
                        .param("from", from.toString()).param("to", from.plusSeconds(900).toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
                .andExpect(jsonPath("$[0].dataType").value("PREDICTED"))
                .andExpect(jsonPath("$[0].observed").value(false)).andExpect(jsonPath("$[0].predicted").value(true));
    }

    @Test void validatesCellIntervalAndHistoryRange() throws Exception {
        Instant from = Instant.ofEpochSecond(Math.floorDiv(Instant.now().getEpochSecond(), 900) * 900 + 900);
        mvc.perform(get("/api/pollution/forecast").param("cell", "unknown")
                        .param("from", from.toString()).param("interval", "15"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/pollution/forecast").param("cell", "demo-delhi-central")
                        .param("from", from.toString()).param("interval", "60"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast/history").param("cell", "demo-delhi-central")
                        .param("from", from.toString()).param("to", from.minusSeconds(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test void missingForecastAndHistoryParametersReturnBadRequest() throws Exception {
        mvc.perform(get("/api/pollution/forecast"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast").param("cell", "demo-delhi-central"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast").param("from", "2026-09-29T12:00:00Z"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/pollution/forecast/history"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast/history").param("cell", "demo-delhi-central")
                        .param("from", "2026-09-28T12:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast/history").param("cell", "demo-delhi-central")
                        .param("to", "2026-09-29T12:00:00Z"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast/history").param("from", "2026-09-28T12:00:00Z")
                        .param("to", "2026-09-29T12:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test void malformedForecastTimestampAndInvalidHistoryLimitReturnBadRequest() throws Exception {
        mvc.perform(get("/api/pollution/forecast").param("cell", "demo-delhi-central")
                        .param("from", "not-a-timestamp"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/pollution/forecast/history").param("cell", "demo-delhi-central")
                        .param("from", "2026-09-28T12:00:00Z").param("to", "2026-09-29T12:00:00Z")
                        .param("limit", "0"))
                .andExpect(status().isBadRequest());
    }
}
