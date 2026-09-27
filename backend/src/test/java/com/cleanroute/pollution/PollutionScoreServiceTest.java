package com.cleanroute.pollution;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.domain.ObservationModels.TrafficObservation;
import com.cleanroute.observation.domain.ObservationModels.WeatherObservation;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionScoreService;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PollutionScoreServiceTest {
    private final PollutionScoringProperties properties = new PollutionScoringProperties();
    private final PollutionScoreService service = new PollutionScoreService(new PollutionEngine(properties), properties);
    private final Instant time = Instant.parse("2026-09-27T09:00:00Z");
    private final PollutionObservation pollution = new PollutionObservation("cell", time, 50, 17.5,
            null, null, null, null, null, "aqi-demo", true);

    @Test void combinesPollutionTrafficWeatherDurationDistanceAndModeWithExplanations() {
        WeatherObservation weather = new WeatherObservation("cell", time, 25.0, 50.0,
                0.0, 120.0, 0.0, "CLEAR", "weather-demo", true);
        TrafficObservation traffic = new TrafficObservation("cell", time, "HEAVY", 5.0, 10.0, "traffic-demo", true);
        var score = service.score(pollution, weather, traffic, 1800, 5000, TravelMode.WALK);
        assertThat(score.score()).isEqualTo(62.5);
        assertThat(score.components()).extracting(c -> c.name())
                .containsExactly("pollutionExposure", "trafficContext", "weatherContext");
        assertThat(score.components()).extracting(c -> c.appliedWeight()).containsExactly(0.75, 0.15, 0.10);
        assertThat(score.direction()).contains("Higher means greater");
        assertThat(score.caveats()).anyMatch(c -> c.contains("not validated"));
        assertThat(score.generated()).isTrue();
    }

    @Test void usesAvailableComponentsOnlyAndRouteContextChangesExposure() {
        var baseline = service.score(pollution, null, null, 1800, 5000, TravelMode.WALK);
        var shortRoute = service.score(pollution, null, null, 900, 2500, TravelMode.WALK);
        var jog = service.score(pollution, null, null, 1800, 5000, TravelMode.JOG);
        assertThat(baseline.score()).isEqualTo(50.0);
        assertThat(shortRoute.score()).isEqualTo(25.0);
        assertThat(jog.score()).isEqualTo(75.0);
        assertThat(baseline.components()).hasSize(1);
        assertThat(baseline.caveats()).anyMatch(c -> c.contains("Weather context was unavailable"));
        assertThat(baseline.caveats()).anyMatch(c -> c.contains("Traffic context was unavailable"));
    }

    @Test void clampsScoresToDocumentedRangeAndRejectsInvalidInputs() {
        PollutionObservation high = new PollutionObservation("cell", time, 500, 350.0,
                null, null, null, null, null, "aqi-demo", true);
        var score = service.score(high, null, null, 86400, 200000, TravelMode.JOG);
        assertThat(score.score()).isBetween(0.0, 100.0);
        assertThatThrownBy(() -> service.score(pollution, null, null, 0, 50, TravelMode.WALK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.score(pollution, null, null, 1, Double.NaN, TravelMode.WALK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void appliesConfiguredComponentWeights() {
        properties.setPollutionWeight(0.5);
        properties.setTrafficWeight(0.5);
        properties.setWeatherWeight(0.0);
        TrafficObservation traffic = new TrafficObservation("cell", time, "HEAVY", 5.0, 10.0, "traffic-demo", false);
        var score = service.score(pollution, null, traffic, 1800, 5000, TravelMode.WALK);
        assertThat(score.score()).isEqualTo(75.0);
        assertThat(score.components()).extracting(c -> c.appliedWeight()).containsExactly(0.5, 0.5);
    }
}
