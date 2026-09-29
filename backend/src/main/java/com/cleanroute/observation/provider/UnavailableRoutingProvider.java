package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** Keeps invalid routing configuration explicit instead of silently using mock geometry. */
@Component
@ConditionalOnMissingBean({MockRoutingProvider.class, OSRMRoutingProvider.class})
public class UnavailableRoutingProvider implements RoutingProvider {
    @Override
    public String providerId() {
        return "unavailable";
    }

    @Override
    public RoutePath route(RoutingRequest request) {
        throw ProviderFailureException.temporary();
    }
}
