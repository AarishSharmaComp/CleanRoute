package com.cleanroute.geocoding;

import com.cleanroute.geocoding.provider.GeocodingProvider;
import com.cleanroute.geocoding.domain.GeocodingModels.LocationSearchResult;
import com.cleanroute.observation.provider.ProviderFailureException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:place-search-api;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000", "app.geocoding.provider=photon"
})
@AutoConfigureMockMvc
class PlaceSearchApiTest {
    @Autowired MockMvc mvc;
    @MockBean(name = "photonGeocodingProvider") GeocodingProvider photonGeocodingProvider;

    @Test void publicSearchReturnsCleanPlaceDto() throws Exception {
        when(photonGeocodingProvider.providerId()).thenReturn("photon-geocoding");
        when(photonGeocodingProvider.search("New York")).thenReturn(List.of(
                new LocationSearchResult("New York", "New York, United States", "United States", 40.7128, -74.006, false)));
        mvc.perform(get("/api/places/search").param("q", "New York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("New York"))
                .andExpect(jsonPath("$[0].latitude").value(40.7128))
                .andExpect(jsonPath("$[0].supportedArea").value(false));
    }

    @Test void publicSearchRejectsShortQueryAndMapsProviderFailureWithoutDetails() throws Exception {
        mvc.perform(get("/api/places/search").param("q", "NY"))
                .andExpect(status().isBadRequest());
        when(photonGeocodingProvider.providerId()).thenReturn("photon-geocoding");
        when(photonGeocodingProvider.search("Paris")).thenThrow(ProviderFailureException.temporary());
        String response = mvc.perform(get("/api/places/search").param("q", "Paris"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Location search provider is temporarily unavailable"))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(response).doesNotContain("Photon");
    }
}
