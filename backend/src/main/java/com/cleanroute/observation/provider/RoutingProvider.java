package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;

/** Provider-neutral path lookup. Ranking and pollution-based selection belong to later phases. */
public interface RoutingProvider {
    String providerId();
    RoutePath route(RoutingRequest request);
}
