package com.cleanroute.route;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.route.service.RouteSampler;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class RouteSamplerTest {
    private final Coordinate start = new Coordinate(28.60, 77.20);
    private final Coordinate end = new Coordinate(28.70, 77.20);

    @Test void includesEndpointsAndKeepsSamplesOrderedAlongEntireLongRoute() {
        var samples = RouteSampler.sample(List.of(start, end), 1000);
        assertThat(samples.size()).isGreaterThan(5);
        assertThat(samples.getFirst().coordinate()).isEqualTo(start);
        assertThat(samples.getLast().coordinate()).isEqualTo(end);
        assertThat(samples).extracting(RouteSampler.Sample::distanceFromStartMeters)
                .isSorted().doesNotContainNull();
        assertThat(samples.getLast().distanceFromStartMeters()).isGreaterThan(10_000);
    }

    @Test void shortRouteStillHasTwoEndpoints() {
        var samples = RouteSampler.sample(List.of(start, new Coordinate(28.6001, 77.20)), 1000);
        assertThat(samples).hasSize(2);
        assertThat(samples.getFirst().coordinate()).isEqualTo(start);
    }
}
