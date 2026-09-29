package com.cleanroute.observation;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.provider.RoutingProvider;
import com.cleanroute.observation.provider.UnavailableRoutingProvider;
import com.cleanroute.domain.TravelMode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "app.routing.provider=invalid-provider",
        "spring.datasource.url=jdbc:h2:mem:routing-provider-unavailable;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"
})
class UnavailableRoutingProviderTest {
    @Autowired RoutingProvider provider;

    @Test void invalidProviderIsExplicitlyUnavailableWithoutMockFallback() {
        assertThat(provider).isInstanceOf(UnavailableRoutingProvider.class);
        assertThatThrownBy(() -> provider.route(new RoutingRequest(
                new Coordinate(28.6, 77.2), new Coordinate(28.7, 77.3), TravelMode.CAR)))
                .isInstanceOf(ProviderFailureException.class);
    }
}
