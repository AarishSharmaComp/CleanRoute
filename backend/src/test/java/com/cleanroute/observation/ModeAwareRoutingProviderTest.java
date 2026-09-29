package com.cleanroute.observation;

import com.cleanroute.observation.provider.ModeAwareRoutingProvider;
import com.cleanroute.observation.provider.RoutingProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "app.routing.provider=mode-aware",
        "spring.datasource.url=jdbc:h2:mem:mode-aware-routing;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "app.jwt.secret=test-signing-secret-that-is-more-than-32-bytes-long",
        "app.observations.initial-delay-ms=3600000"
})
class ModeAwareRoutingProviderTest {
    @Autowired RoutingProvider provider;

    @Test void selectsModeAwareProviderWhenConfigured() {
        assertThat(provider).isInstanceOf(ModeAwareRoutingProvider.class);
    }
}
