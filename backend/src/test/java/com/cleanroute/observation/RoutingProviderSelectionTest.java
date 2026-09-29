package com.cleanroute.observation;

import com.cleanroute.observation.provider.OSRMRoutingProvider;
import com.cleanroute.observation.provider.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.routing.provider=osrm",
        "spring.datasource.url=jdbc:h2:mem:routing-provider-selection;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"
})
class RoutingProviderSelectionTest {
    @Autowired RoutingProvider provider;

    @Test void selectsOSRMProviderOnlyWhenConfigured() {
        assertThat(provider).isInstanceOf(OSRMRoutingProvider.class);
    }
}
