package com.cleanroute.observation;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.observation.repository.ProviderFreshnessRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.UUID;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:phase3-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1","spring.datasource.driver-class-name=org.h2.Driver","spring.datasource.username=sa","spring.datasource.password=","spring.jpa.database-platform=org.hibernate.dialect.H2Dialect","app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long","app.observations.initial-delay-ms=3600000"})
@AutoConfigureMockMvc
class ObservationPersistenceTest {
    @Autowired ObservationRepository repository; @Autowired ProviderFreshnessRepository freshness;
    @Autowired JdbcTemplate jdbc; @Autowired MockMvc mvc;
    @Test void migrationPersistsDeduplicatesAndSupportsLatestAndHistory() throws Exception {
        String cell="phase3-persistence-cell"; Instant t=Instant.parse("2026-09-27T09:00:00Z");
        repository.saveCell(new com.cleanroute.observation.domain.ObservationModels.GeographicCell(cell,28.6,77.2));
        var observation=new PollutionObservation(cell,t,55,18.0,35.0,17.0,null,null,22.0,"test-provider",true);
        repository.save(observation); repository.save(observation);
        mvc.perform(get("/api/aqi/current").param("cell",cell)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aqi").value(55)).andExpect(jsonPath("$.generated").value(true))
                .andExpect(jsonPath("$.so2").doesNotExist()).andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.units.pm25").value("µg/m³"));
        mvc.perform(get("/api/aqi/history").param("cell",cell).param("start",t.minusSeconds(60).toString()).param("end",t.plusSeconds(60).toString()).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$[0].provider").value("test-provider"))
                .andExpect(jsonPath("$[0].units.co").value("mg/m³"));
    }

    @Test void unknownCellReturns404ForCurrentAndHistoryButKnownEmptyCellReturnsEmptyHistory() throws Exception {
        String emptyCell="phase3-empty-cell";
        repository.saveCell(new com.cleanroute.observation.domain.ObservationModels.GeographicCell(emptyCell,28.6,77.2));
        mvc.perform(get("/api/aqi/current").param("cell","phase3-unknown-cell")).andExpect(status().isNotFound());
        mvc.perform(get("/api/aqi/history").param("cell","phase3-unknown-cell").param("start","2026-09-27T00:00:00Z").param("end","2026-09-27T01:00:00Z"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/aqi/history").param("cell",emptyCell).param("start","2026-09-27T00:00:00Z").param("end","2026-09-27T01:00:00Z"))
                .andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
    }

    @Test void providerFreshnessPersistsLastSuccessAndLaterFailure() {
        String cell="demo-delhi-central"; Instant successAt=Instant.parse("2026-09-27T09:00:00Z");
        freshness.recordSuccess("freshness-test",cell,successAt);
        freshness.recordFailure("freshness-test",cell,successAt.plusSeconds(60),"TIMEOUT");
        var status=freshness.find("freshness-test",cell).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(status.lastSuccessAt()).isEqualTo(successAt);
        org.assertj.core.api.Assertions.assertThat(status.lastFailureType()).isEqualTo("TIMEOUT");
        org.assertj.core.api.Assertions.assertThat(status.lastFailureAt()).isEqualTo(successAt.plusSeconds(60));
    }

    @Test void migrationAddsDatabaseCoordinateConstraintsForSavedRoutes() {
        Integer constraints=jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.table_constraints WHERE upper(table_name)='SAVED_ROUTE' AND lower(constraint_name) LIKE 'ck_saved_route_%'",Integer.class);
        org.assertj.core.api.Assertions.assertThat(constraints).isEqualTo(4);
    }

    @Test void databaseRejectsSavedRouteCoordinatesOutsideGeographicRange() {
        UUID userId=UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,email,password_hash,display_name) VALUES (?,?,?,?)",userId,"coordinate-"+userId+"@example.test","$2a$10$testhash","Coordinate Test");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> jdbc.update("INSERT INTO saved_route(id,user_id,origin_name,origin_latitude,origin_longitude,destination_name,destination_latitude,destination_longitude,travel_mode,route_preference) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(),userId,"A",91.0,0.0,"B",10.0,10.0,"WALK","BALANCED"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
