package com.cleanroute.observation;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.MockRoutingProvider;
import com.cleanroute.observation.provider.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.assertj.core.api.Assertions.assertThat;

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
}
