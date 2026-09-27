package com.cleanroute.observation;

import com.cleanroute.observation.config.ObservationProperties;
import com.cleanroute.observation.service.ObservationIngestionScheduler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;

class ObservationSchedulerConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class, ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfiguration.class);

    @Test void defaultIntervalIsFifteenMinutesAndNormalConfiguredIntervalIsAccepted() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ObservationProperties.class).getIngestionIntervalMs()).isEqualTo(900_000);
        });
        runner.withPropertyValues("app.observations.ingestion-interval-ms=60000").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ObservationProperties.class).getIngestionIntervalMs()).isEqualTo(60_000);
        });
    }

    @Test void zeroNegativeAndUnreasonablySmallIntervalsFailConfigurationBinding() {
        for (String interval : new String[]{"0", "-1", "1000"}) {
            runner.withPropertyValues("app.observations.ingestion-interval-ms=" + interval)
                    .run(context -> assertThat(context).hasFailed());
        }
    }

    @Test void schedulerUsesTheValidatedFifteenMinuteConfigurationKey() throws Exception {
        var method = ObservationIngestionScheduler.class.getMethod("scheduledIngestion");
        Scheduled scheduled = method.getAnnotation(Scheduled.class);
        assertThat(scheduled.fixedDelayString()).isEqualTo("${app.observations.ingestion-interval-ms:900000}");
        assertThat(scheduled.initialDelayString()).isEqualTo("${app.observations.initial-delay-ms:900000}");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ObservationProperties.class)
    static class PropertiesConfiguration {}
}
