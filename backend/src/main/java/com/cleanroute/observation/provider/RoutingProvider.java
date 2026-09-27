package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import java.util.List;

/** Provider-neutral path lookup. Ranking and pollution-based selection belong to later phases. */
public interface RoutingProvider {
    String providerId();
    RoutePath route(RoutingRequest request);
    default List<RoutePath> alternatives(RoutingRequest request) { return List.of(route(request)); }
}
