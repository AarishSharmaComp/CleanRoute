package com.cleanroute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.cleanroute.observation.config.ObservationProperties;
import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.route.config.RouteSuitabilityProperties;
import com.cleanroute.notification.config.NotificationProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({ObservationProperties.class, EnvironmentalProperties.class, PollutionScoringProperties.class, RouteSuitabilityProperties.class, NotificationProperties.class})
public class CleanRouteApplication {
    public static void main(String[] args) {
        SpringApplication.run(CleanRouteApplication.class, args);
    }
}
