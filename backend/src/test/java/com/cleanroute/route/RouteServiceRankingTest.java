package com.cleanroute.route;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.RoutingProvider;
import com.cleanroute.observation.provider.MockRoutingProvider;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionForecastService;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationRequest;
import com.cleanroute.route.config.RouteSuitabilityProperties;
import com.cleanroute.route.repository.RouteCalculationRepository;
import com.cleanroute.route.service.RouteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class RouteServiceRankingTest {
    @Test void mockAlternativeDistancesMatchTheirReturnedGeometryAndRemainDeterministic() {
        var provider = new MockRoutingProvider();
        var request = new RoutingRequest(new Coordinate(28.6139, 77.2090),
                new Coordinate(28.7041, 77.1025), TravelMode.WALK);

        var alternatives = provider.alternatives(request);

        assertThat(alternatives).containsExactlyElementsOf(provider.alternatives(request));
        for (RoutePath path : alternatives) {
            double geometryDistance = 0;
            for (int i = 1; i < path.geometry().size(); i++)
                geometryDistance += haversine(path.geometry().get(i - 1), path.geometry().get(i));
            assertThat(path.distanceMeters()).isCloseTo(geometryDistance, within(0.01));
        }
    }

    @Test void preferencesProduceDifferentRankedAlternativesWithExplanations() {
        ObservationRepository observations = mock(ObservationRepository.class);
        RoutingProvider routing = mock(RoutingProvider.class);
        PollutionForecastService forecasts = mock(PollutionForecastService.class);
        RouteCalculationRepository storage = mock(RouteCalculationRepository.class);
        var central = new GeographicCell("central", 28.6139, 77.2090);
        var south = new GeographicCell("south", 28.5355, 77.2100);
        var north = new GeographicCell("north", 28.7041, 77.1025);
        when(observations.cells()).thenReturn(List.of(central, south, north));
        var fastOrigin = new Coordinate(28.6138, 77.2089);
        var fastDest = new Coordinate(28.6140, 77.2091);
        var cleanOrigin = new Coordinate(28.5354, 77.2099);
        var cleanDest = new Coordinate(28.5356, 77.2101);
        var balancedOrigin = new Coordinate(28.7040, 77.1024);
        var balancedDest = new Coordinate(28.7042, 77.1026);
        when(routing.alternatives(any(RoutingRequest.class))).thenReturn(List.of(
                new RoutePath(List.of(fastOrigin, fastDest), 100, 100, "mock", true, "fast"),
                new RoutePath(List.of(cleanOrigin, cleanDest), 300, 300, "mock", true, "clean"),
                new RoutePath(List.of(balancedOrigin, balancedDest), 200, 200, "mock", true, "balanced")));
        when(forecasts.forecast(anyString(), any(Instant.class))).thenAnswer(call -> {
            String cell = call.getArgument(0);
            double pm25 = switch (cell) { case "central" -> 24.5; case "south" -> 3.5; default -> 7.0; };
            return new PollutionForecast(cell, call.getArgument(1), Instant.now(), null, pm25,
                    null, null, null, null, null, 80, "HIGH", 4, "test", "test-v1", true);
        });
        var service = new RouteService(routing, observations, forecasts,
                new PollutionEngine(new PollutionScoringProperties()), storage, new ObjectMapper().findAndRegisterModules());
        UUID owner = UUID.randomUUID();
        var origin = new Coordinate(28.6, 77.2); var destination = new Coordinate(28.7, 77.3);

        var fastest = service.calculate(new CalculationRequest(origin, destination, TravelMode.CAR, RoutePreference.FASTEST, null), owner);
        var cleanest = service.calculate(new CalculationRequest(origin, destination, TravelMode.CAR, RoutePreference.CLEANEST, null), owner);
        var balanced = service.calculate(new CalculationRequest(origin, destination, TravelMode.CAR, RoutePreference.BALANCED, null), owner);
        var jogger = service.calculate(new CalculationRequest(origin, destination, TravelMode.JOG, RoutePreference.JOGGER, null), owner);
        var cyclist = service.calculate(new CalculationRequest(origin, destination, TravelMode.CYCLE, RoutePreference.CYCLIST, null), owner);
        assertThat(fastest.alternatives().getFirst().alternativeId()).isEqualTo("fast");
        assertThat(cleanest.alternatives().getFirst().alternativeId()).isEqualTo("clean");
        assertThat(balanced.alternatives().getFirst().alternativeId()).isEqualTo("balanced");
        assertThat(cleanest.alternatives().getFirst().expectedPollutionExposure()).isEqualTo(10.0);
        assertThat(cleanest.alternatives().getFirst().selectionReason().headline()).isEqualTo("Lowest modeled pollution exposure");
        assertThat(cleanest.alternatives().getFirst().selectionReason().comparison().availableRoutes()).isEqualTo(3);
        assertThat(balanced.alternatives().getFirst().scoreComponents()).containsKeys("durationEfficiency", "pollutionCleanliness");
        assertThat(balanced.alternatives().getFirst().selectionReason().factors()).anyMatch(factor -> factor.contains("equal-weight"));
        assertThat(fastest.alternatives().getFirst().reasons()).isNotEmpty();
        assertThat(jogger.alternatives().getFirst().alternativeId()).isEqualTo("clean");
        assertThat(jogger.alternatives().getFirst().scoreComponents()).containsKey("distanceSuitability");
        assertThat(jogger.alternatives().getFirst().reasons()).anyMatch(reason -> reason.contains("Green-area metadata is unavailable"));
        assertThat(cyclist.alternatives().getFirst().alternativeId()).isEqualTo("clean");
        assertThat(cyclist.alternatives().getFirst().reasons()).anyMatch(reason -> reason.contains("Cycling-compatibility metadata is unavailable"));
        verify(storage, times(5)).save(any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), anyString(), anyString(), anyString());
    }

    @Test void cleanestDoesNotClaimComparisonWhenOnlyOneRouteOrNoExposureIsAvailable() {
        ObservationRepository observations = mock(ObservationRepository.class);
        RoutingProvider routing = mock(RoutingProvider.class);
        PollutionForecastService forecasts = mock(PollutionForecastService.class);
        RouteCalculationRepository storage = mock(RouteCalculationRepository.class);
        Coordinate origin = new Coordinate(28.6139, 77.2090), destination = new Coordinate(28.6200, 77.2150);
        when(observations.cells()).thenReturn(List.of(new GeographicCell("cell", 28.6139, 77.2090)));
        when(routing.alternatives(any(RoutingRequest.class))).thenReturn(List.of(
                new RoutePath(List.of(origin, destination), 1000, 600, "osrm", false, "only-route")));
        when(forecasts.forecast(anyString(), any(Instant.class))).thenReturn(new PollutionForecast("cell", Instant.now(), Instant.now(),
                null, null, null, null, null, null, null, 0, "UNAVAILABLE", 0, "test", "test", false));
        var service = new RouteService(routing, observations, forecasts,
                new PollutionEngine(new PollutionScoringProperties()), storage, new ObjectMapper().findAndRegisterModules());

        var result = service.calculate(new CalculationRequest(origin, destination, TravelMode.CAR, RoutePreference.CLEANEST, null), UUID.randomUUID());

        assertThat(result.alternatives()).hasSize(1);
        assertThat(result.alternatives().getFirst().expectedPollutionExposure()).isNull();
        assertThat(result.alternatives().getFirst().selectionReason().summary())
                .contains("Only one routing candidate was available")
                .contains("could not be established");
    }

    @Test void joggerRankingUsesAvailableGreenAreaCoverageAndExplainsItsUse() {
        var result = calculateJoggerWithGreenCoverage(1.0, 0.0);

        assertThat(result.alternatives().getFirst().alternativeId()).isEqualTo("green-route");
        assertThat(result.alternatives().getFirst().scoreComponents().get("greenAreaPreference")).isEqualTo(100.0);
        assertThat(result.alternatives().getLast().scoreComponents().get("greenAreaPreference")).isEqualTo(0.0);
        assertThat(result.alternatives().getFirst().reasons())
                .anyMatch(reason -> reason.contains("Green-area coverage metadata was used"));
    }

    @Test void joggerRankingDoesNotInventMissingGreenAreaCoverageAndExplainsItsAbsence() {
        var result = calculateJoggerWithGreenCoverage(null, null);

        assertThat(result.alternatives()).allSatisfy(alternative -> {
            assertThat(alternative.scoreComponents()).doesNotContainKey("greenAreaPreference");
            assertThat(alternative.reasons()).anyMatch(reason -> reason.contains("Green-area metadata is unavailable and was not used"));
        });
    }

    private static com.cleanroute.route.domain.RoutePlanningModels.CalculationResult calculateJoggerWithGreenCoverage(
            Double greenerCoverage, Double lowerCoverage) {
        ObservationRepository observations = mock(ObservationRepository.class);
        RoutingProvider routing = mock(RoutingProvider.class);
        PollutionForecastService forecasts = mock(PollutionForecastService.class);
        RouteCalculationRepository storage = mock(RouteCalculationRepository.class);
        when(observations.cells()).thenReturn(List.of(new GeographicCell("central", 28.6139, 77.2090)));
        Coordinate origin = new Coordinate(28.6139, 77.2090);
        Coordinate destination = new Coordinate(28.6200, 77.2150);
        when(routing.alternatives(any(RoutingRequest.class))).thenReturn(List.of(
                new RoutePath(List.of(origin, destination), 5000, 1200, "mock", true, "green-route",
                        greenerCoverage, null, null),
                new RoutePath(List.of(origin, destination), 5000, 1200, "mock", true, "lower-green-route",
                        lowerCoverage, null, null)));
        when(forecasts.forecast(anyString(), any(Instant.class))).thenAnswer(call ->
                new PollutionForecast("central", call.getArgument(1), Instant.now(), null, 10.0,
                        null, null, null, null, null, 90, "HIGH", 5, "test", "test-v1", true));
        RouteSuitabilityProperties suitability = new RouteSuitabilityProperties();
        suitability.setPreferGreenAreas(true);
        var service = new RouteService(routing, observations, forecasts,
                new PollutionEngine(new PollutionScoringProperties()), storage,
                new ObjectMapper().findAndRegisterModules(), suitability);

        return service.calculate(new CalculationRequest(origin, destination, TravelMode.JOG, RoutePreference.JOGGER, null),
                UUID.randomUUID());
    }

    private static double haversine(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
