package com.cleanroute.observation;

import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.observation.provider.EnvironmentalDataProvider;
import com.cleanroute.observation.provider.MockEnvironmentalProvider;
import com.cleanroute.observation.provider.OpenMeteoEnvironmentalProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionEvaluationReport;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class EnvironmentalProviderSelectionTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ProviderConfiguration.class);

    @Test void mockIsSafeDefault() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(EnvironmentalDataProvider.class)).isInstanceOf(MockEnvironmentalProvider.class);
        });
    }

    @Test void openMeteoIsSelectedWhenConfigured() {
        runner.withPropertyValues("app.environmental.provider=open-meteo")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(EnvironmentalDataProvider.class)).isInstanceOf(OpenMeteoEnvironmentalProvider.class);
        });
    }

    @Test void invalidProviderDoesNotSilentlySelectMock() {
        runner.withPropertyValues("app.environmental.provider=unknown")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(EnvironmentalDataProvider.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({EnvironmentalProperties.class, MockEnvironmentalProvider.class, OpenMeteoEnvironmentalProvider.class})
    static class ProviderConfiguration {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
    }
}
