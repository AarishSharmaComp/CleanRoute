package com.cleanroute;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.cleanroute.observation.config.ObservationProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(ObservationProperties.class)
public class CleanRouteApplication {
    public static void main(String[] args) {
        SpringApplication.run(CleanRouteApplication.class, args);
    }
}
