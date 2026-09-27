package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import org.springframework.stereotype.Component;
import java.util.List;

/** Deterministic straight-line demo path; it does not choose or rank routes. */
@Component
public class MockRoutingProvider implements RoutingProvider {
    @Override public String providerId() { return "mock-demo-routing"; }
    @Override public RoutePath route(RoutingRequest request) {
        Coordinate origin = request.origin();
        Coordinate destination = request.destination();
        double distance = haversineMeters(origin, destination);
        return new RoutePath(List.of(origin, destination), distance, (int) Math.ceil(distance / 4.0), providerId(), true);
    }
    private static double haversineMeters(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
