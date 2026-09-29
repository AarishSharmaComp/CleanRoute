package com.cleanroute.observation;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.MockRoutingProvider;
import com.cleanroute.observation.provider.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@SpringJUnitConfig(MockRoutingProvider.class)
class RoutingProviderTest {
    @Autowired RoutingProvider provider;

    @Test void mockRoutingProviderIsInjectedAndReturnsDeterministicGeneratedPathWithoutRanking() {
        var request = new RoutingRequest(new Coordinate(28.6, 77.2), new Coordinate(28.7, 77.3));
        var first = provider.route(request);
        assertThat(provider).isInstanceOf(MockRoutingProvider.class);
        assertThat(provider.route(request)).isEqualTo(first);
        assertThat(first.provider()).isEqualTo("mock-demo-routing");
        assertThat(first.generated()).isTrue();
        assertThat(first.geometry()).containsExactly(request.origin(), request.destination());
    }

    @Test void mockAlternativesRemainDeterministicAndMatchTheirGeometryDistances() {
        var request = new RoutingRequest(new Coordinate(28.6139, 77.2090),
                new Coordinate(28.7041, 77.1025));
        var alternatives = provider.alternatives(request);
        assertThat(alternatives).hasSize(3).containsExactlyElementsOf(provider.alternatives(request));
        for (var route : alternatives) {
            double geometryDistance = 0;
            for (int i = 1; i < route.geometry().size(); i++)
                geometryDistance += haversine(route.geometry().get(i - 1), route.geometry().get(i));
            assertThat(route.distanceMeters()).isCloseTo(geometryDistance, within(0.01));
        }
    }

    private static double haversine(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
