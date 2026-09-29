package com.cleanroute.route;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.observation.config.RoutingProperties;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.*;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionForecastService;
import com.cleanroute.route.config.RouteSuitabilityProperties;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationRequest;
import com.cleanroute.route.repository.RouteCalculationRepository;
import com.cleanroute.route.service.RouteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RouteCoordinateExposureTest {
    @Test void localOsrmRouteFlowsIntoEnvironmentalSamplingAndExposureScoring() throws Exception {
        HttpServer osrm = HttpServer.create(new InetSocketAddress(0), 0);
        osrm.createContext("/", exchange -> {
            byte[] body = "{\"code\":\"Ok\",\"routes\":[{\"distance\":2200,\"duration\":900,\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[77.20,28.60],[77.20,28.62]]}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close();
        });
        osrm.start();
        try {
            var routingProperties = new RoutingProperties();
            routingProperties.setOsrmUrl("http://localhost:" + osrm.getAddress().getPort());
            routingProperties.setReadTimeoutMs(1000);
            var osrmProvider = new com.cleanroute.observation.provider.OSRMRoutingProvider(routingProperties, new ObjectMapper());
            var env = mock(EnvironmentalDataProvider.class);
            when(env.providerId()).thenReturn("open-meteo-air-quality");
            when(env.supportsCoordinateLookup()).thenReturn(true);
            when(env.observationsAt(anyList())).thenAnswer(call -> ((List<EnvironmentalCoordinateQuery>) call.getArgument(0)).stream()
                    .map(q -> new EnvironmentalCoordinateResult(q, new PollutionObservation("coordinate", q.observedAt(),
                            null, 20.0, null, null, null, null, null, "open-meteo-air-quality", false))).toList());

            var route = service(osrmProvider, env).calculate(request(), UUID.randomUUID()).alternatives().getFirst();

            assertThat(route.provider()).isEqualTo("osrm");
            assertThat(route.distanceMeters()).isEqualTo(2200);
            assertThat(route.durationSeconds()).isEqualTo(900);
            assertThat(route.geometry()).hasSize(2);
            assertThat(route.observationSource()).isEqualTo("open-meteo-air-quality");
            assertThat(route.availableSampleCount()).isPositive();
            assertThat(route.expectedPollutionExposure()).isNotNull();
        } finally { osrm.stop(0); }
    }

    @Test void routeSamplesUsePassageTimesAndCoordinateMeasurementsReachPollutionEngine() {
        var routing = routing();
        var env = mock(EnvironmentalDataProvider.class);
        when(env.providerId()).thenReturn("open-meteo-air-quality");
        when(env.supportsCoordinateLookup()).thenReturn(true);
        when(env.observationsAt(anyList())).thenAnswer(call -> {
            List<EnvironmentalCoordinateQuery> queries = call.getArgument(0);
            assertThat(queries).hasSize(2);
            assertThat(queries.get(0).observedAt()).isBefore(queries.get(1).observedAt());
            assertThat(java.time.Duration.between(queries.get(0).observedAt(), queries.get(1).observedAt()).toSeconds())
                    .isEqualTo(900);
            return queries.stream().map(q -> new EnvironmentalCoordinateResult(q,
                    new PollutionObservation("coordinate", q.observedAt(), null, 17.5, 24.0, null, null, null, null,
                            "open-meteo-air-quality", false))).toList();
        });
        var result = service(routing, env).calculate(request(), UUID.randomUUID());

        var route = result.alternatives().getFirst();
        assertThat(route.provider()).isEqualTo("osrm");
        assertThat(route.generated()).isFalse();
        assertThat(route.environmentalCoverage()).isEqualTo("complete");
        assertThat(route.environmentalProvider()).isEqualTo("open-meteo-air-quality");
        assertThat(route.observationSource()).isEqualTo("open-meteo-air-quality");
        assertThat(route.sampledPointCount()).isEqualTo(2);
        assertThat(route.availableSampleCount()).isEqualTo(2);
        assertThat(route.unavailableSampleCount()).isZero();
        assertThat(route.expectedPollutionExposure()).isNotNull().isPositive();
        verify(env).observationsAt(anyList());
    }

    @Test void partialCoordinateCoverageUsesOnlyAvailableMeasurements() {
        var env = mock(EnvironmentalDataProvider.class);
        when(env.providerId()).thenReturn("open-meteo-air-quality");
        when(env.supportsCoordinateLookup()).thenReturn(true);
        when(env.observationsAt(anyList())).thenAnswer(call -> {
            List<EnvironmentalCoordinateQuery> queries = call.getArgument(0);
            return List.of(new EnvironmentalCoordinateResult(queries.getFirst(),
                    new PollutionObservation("coordinate", queries.getFirst().observedAt(), null, 20.0,
                            null, null, null, null, null, "open-meteo-air-quality", false)),
                    new EnvironmentalCoordinateResult(queries.get(1), null));
        });
        var route = service(routing(), env).calculate(request(), UUID.randomUUID()).alternatives().getFirst();

        assertThat(route.environmentalCoverage()).isEqualTo("partial");
        assertThat(route.environmentalProvider()).isEqualTo("open-meteo-air-quality");
        assertThat(route.sampledPointCount()).isEqualTo(2);
        assertThat(route.availableSampleCount()).isEqualTo(1);
        assertThat(route.unavailableSampleCount()).isEqualTo(1);
        assertThat(route.environmentalCoveragePercent()).isEqualTo(50.0);
        assertThat(route.expectedPollutionExposure()).isNotNull();
    }

    @Test void realCoordinateModeDoesNotRequireFixedGeographicCells() {
        var env = mock(EnvironmentalDataProvider.class);
        when(env.providerId()).thenReturn("open-meteo-air-quality");
        when(env.supportsCoordinateLookup()).thenReturn(true);
        when(env.observationsAt(anyList())).thenAnswer(call -> ((List<EnvironmentalCoordinateQuery>) call.getArgument(0)).stream()
                .map(q -> new EnvironmentalCoordinateResult(q, new PollutionObservation("coordinate", q.observedAt(),
                        null, 20.0, null, null, null, null, null, "open-meteo-air-quality", false))).toList());
        var observations = mock(ObservationRepository.class);
        when(observations.cells()).thenReturn(List.of());
        var limits = new EnvironmentalProperties();

        var service = new RouteService(routing(), observations, mock(PollutionForecastService.class),
                new PollutionEngine(new PollutionScoringProperties()), mock(RouteCalculationRepository.class),
                new ObjectMapper().findAndRegisterModules(), new RouteSuitabilityProperties(), env, limits);

        assertThat(service.calculate(request(), UUID.randomUUID()).alternatives().getFirst().expectedPollutionExposure())
                .isNotNull();
        verify(env).observationsAt(anyList());
    }

    @Test void unavailableCoordinateMeasurementsStayNullAndDoNotCallForecastFallback() {
        var env = mock(EnvironmentalDataProvider.class);
        when(env.providerId()).thenReturn("open-meteo-air-quality");
        when(env.supportsCoordinateLookup()).thenReturn(true);
        when(env.observationsAt(anyList())).thenThrow(ProviderFailureException.temporary());
        var forecasts = mock(PollutionForecastService.class);
        var service = service(routing(), env, forecasts);

        var route = service.calculate(request(), UUID.randomUUID()).alternatives().getFirst();

        assertThat(route.expectedPollutionExposure()).isNull();
        assertThat(route.forecastQualityScore()).isNull();
        assertThat(route.environmentalCoverage()).isEqualTo("unavailable");
        assertThat(route.availableSampleCount()).isZero();
        assertThat(route.unavailableSampleCount()).isEqualTo(2);
        verifyNoInteractions(forecasts);
    }

    private RouteService service(RoutingProvider routing, EnvironmentalDataProvider environmental) {
        return service(routing, environmental, mock(PollutionForecastService.class));
    }
    private RouteService service(RoutingProvider routing, EnvironmentalDataProvider environmental, PollutionForecastService forecasts) {
        var observations = mock(ObservationRepository.class);
        when(observations.cells()).thenReturn(List.of(new GeographicCell("demo-delhi-central", 28.6139, 77.2090)));
        var limits = new EnvironmentalProperties();
        limits.setRouteSampleIntervalMeters(3000);
        return new RouteService(routing, observations, forecasts, new PollutionEngine(new PollutionScoringProperties()),
                mock(RouteCalculationRepository.class), new ObjectMapper().findAndRegisterModules(),
                new RouteSuitabilityProperties(), environmental, limits, new RoutingProperties());
    }
    private RoutingProvider routing() {
        RoutingProvider routing = mock(RoutingProvider.class);
        when(routing.alternatives(any(RoutingRequest.class))).thenReturn(List.of(new RoutePath(
                List.of(new Coordinate(28.60, 77.20), new Coordinate(28.62, 77.20)), 2224, 900,
                "osrm", false, "osrm-primary")));
        return routing;
    }
    private CalculationRequest request() {
        return new CalculationRequest(new Coordinate(28.60, 77.20), new Coordinate(28.62, 77.20),
                TravelMode.CAR, RoutePreference.CLEANEST, null);
    }
}
