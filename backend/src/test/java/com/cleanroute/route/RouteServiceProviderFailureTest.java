package com.cleanroute.route;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.provider.RoutingProvider;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionForecastService;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationRequest;
import com.cleanroute.route.repository.RouteCalculationRepository;
import com.cleanroute.route.service.RouteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RouteServiceProviderFailureTest {
    @Test void providerFailureBecomesUnavailableRouteResponseWithoutFallbackRoute() {
        RoutingProvider routing = mock(RoutingProvider.class);
        when(routing.alternatives(any(RoutingRequest.class))).thenThrow(ProviderFailureException.timeout());
        ObservationRepository observations = mock(ObservationRepository.class);
        when(observations.cells()).thenReturn(List.of(new GeographicCell("central", 28.6139, 77.2090)));
        PollutionForecastService forecasts = mock(PollutionForecastService.class);
        RouteService service = new RouteService(routing, observations, forecasts,
                new PollutionEngine(new PollutionScoringProperties()), mock(RouteCalculationRepository.class),
                new ObjectMapper().findAndRegisterModules());
        CalculationRequest request = new CalculationRequest(new Coordinate(28.6, 77.2),
                new Coordinate(28.7, 77.3), TravelMode.CAR, RoutePreference.FASTEST, null);

        assertThatThrownBy(() -> service.calculate(request, UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503 SERVICE_UNAVAILABLE");
        verifyNoInteractions(forecasts);
    }
}
