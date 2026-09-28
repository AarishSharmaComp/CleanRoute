package com.cleanroute.route;

import com.cleanroute.route.config.RouteSuitabilityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class RouteSuitabilityConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class,
                    ValidationAutoConfiguration.class))
            .withUserConfiguration(SuitabilityPropertiesConfiguration.class);

    @Test
    void defaultsAndAnyPositiveJoggerWeightTotalAreAccepted() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RouteSuitabilityProperties.class).isJoggerWeightTotalValid()).isTrue();
        });
        runner.withPropertyValues("app.routes.suitability.pollution-weight=0.25",
                        "app.routes.suitability.traffic-weight=0", "app.routes.suitability.distance-weight=0")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void allZeroJoggerWeightsFailConfigurationWithClearValidationMessage() {
        runner.withPropertyValues("app.routes.suitability.pollution-weight=0",
                        "app.routes.suitability.traffic-weight=0", "app.routes.suitability.distance-weight=0")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasStackTraceContaining(
                            "JOGGER pollution, traffic, and distance weights must have a finite positive total");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RouteSuitabilityProperties.class)
    static class SuitabilityPropertiesConfiguration {}
}
