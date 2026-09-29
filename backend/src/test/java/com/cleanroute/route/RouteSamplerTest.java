package com.cleanroute.route;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.route.service.RouteSampler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RouteSamplerTest {
    private final Coordinate start = new Coordinate(28.60, 77.20);
    private final Coordinate end = new Coordinate(28.70, 77.20);

    @Test void includesEndpointsAndCapsLongRoute() {
        var samples = RouteSampler.sample(List.of(start, end), 100, 5);
        assertThat(samples).hasSize(5);
        assertThat(samples.getFirst().coordinate()).isEqualTo(start);
        assertThat(samples.getLast().coordinate()).isEqualTo(end);
        assertThat(samples.get(1).distanceFromStartMeters()).isGreaterThan(0);
    }

    @Test void shortRouteStillHasTwoEndpoints() {
        var samples = RouteSampler.sample(List.of(start, new Coordinate(28.6001, 77.20)), 1000, 12);
        assertThat(samples).hasSize(2);
        assertThat(samples.getFirst().coordinate()).isEqualTo(start);
    }
}
