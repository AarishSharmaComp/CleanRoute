package com.cleanroute.geocoding;

import com.cleanroute.geocoding.config.GeocodingProperties;
import com.cleanroute.geocoding.provider.MockGeocodingProvider;
import com.cleanroute.geocoding.provider.PhotonGeocodingProvider;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.repository.ObservationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeocodingProviderTest {
    private final ObservationRepository observations = mock(ObservationRepository.class);
    private final GeocodingProperties properties = new GeocodingProperties();
    private final PhotonGeocodingProvider photon = new PhotonGeocodingProvider(properties, observations, new ObjectMapper());

    @Test void normalizesPhotonFeatureCoordinatesAndMarksOnlyDemoAreaAsCovered() {
        when(observations.cells()).thenReturn(List.of(new GeographicCell("demo-delhi-central", 28.6139, 77.2090)));
        var results = photon.normalize("""
                {"features":[
                  {"geometry":{"coordinates":[77.209,28.614]},"properties":{"name":"Delhi","city":"Delhi","country":"India"}},
                  {"geometry":{"coordinates":[-0.1278,51.5074]},"properties":{"name":"London","country":"United Kingdom"}}
                ]}
                """);
        assertThat(results).hasSize(2);
        assertThat(results.get(0).displayName()).isEqualTo("Delhi, India");
        assertThat(results.get(0).latitude()).isEqualTo(28.614);
        assertThat(results.get(0).longitude()).isEqualTo(77.209);
        assertThat(results.get(0).supportedArea()).isTrue();
        assertThat(results.get(1).supportedArea()).isFalse();
    }

    @Test void rejectsMalformedPhotonPayloadAndFiltersInvalidFeatures() {
        assertThatThrownBy(() -> photon.normalize("not json"))
                .isInstanceOf(ProviderFailureException.class);
        assertThatThrownBy(() -> photon.normalize("{\"features\":{}}"))
                .isInstanceOf(ProviderFailureException.class);
        assertThat(photon.normalize("""
                {"features":[{"geometry":{"coordinates":[300,91]},"properties":{"name":"Bad"}}]}
                """)).isEmpty();
    }

    @Test void mockSearchIsDeterministicAndNeverInventsCoordinatesForUnknownQueries() {
        var provider = new MockGeocodingProvider();
        assertThat(provider.search("London")).isEqualTo(provider.search("London"));
        assertThat(provider.search("London")).singleElement().satisfies(place -> {
            assertThat(place.supportedArea()).isFalse();
            assertThat(place.latitude()).isEqualTo(51.5074);
        });
        assertThat(provider.search("Some arbitrary place")).isEmpty();
        assertThat(provider.search("NY")).isEmpty();
    }
}
