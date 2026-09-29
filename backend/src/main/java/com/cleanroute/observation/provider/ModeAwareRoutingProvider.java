package com.cleanroute.observation.provider;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.cleanroute.observation.config.RoutingProperties;

import java.util.List;

/** Keeps CAR on OSRM while using Valhalla's pedestrian/bicycle costing for non-car routes. */
@Component
@ConditionalOnProperty(name = "app.routing.provider", havingValue = "mode-aware")
public class ModeAwareRoutingProvider implements RoutingProvider {
    private final OSRMRoutingProvider car;
    private final ValhallaRoutingProvider activeModes;
    private final MockRoutingProvider legacyModes;

    public ModeAwareRoutingProvider(RoutingProperties properties, ObjectMapper mapper) {
        this.car = new OSRMRoutingProvider(properties, mapper);
        this.activeModes = new ValhallaRoutingProvider(properties, mapper);
        this.legacyModes = new MockRoutingProvider();
    }

    @Override public String providerId() { return "mode-aware"; }
    @Override public RoutePath route(RoutingRequest request) { return alternatives(request).getFirst(); }
    @Override public List<RoutePath> alternatives(RoutingRequest request) {
        return request.mode() == TravelMode.CAR ? car.alternatives(request)
                : request.mode() == TravelMode.WALK || request.mode() == TravelMode.CYCLE
                ? activeModes.alternatives(request) : legacyModes.alternatives(request);
    }
}
