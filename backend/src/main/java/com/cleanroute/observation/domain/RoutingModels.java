package com.cleanroute.observation.domain;

import java.util.List;

public final class RoutingModels {
    private RoutingModels() {}
    public record Coordinate(double latitude, double longitude) {}
    public record RoutingRequest(Coordinate origin, Coordinate destination) {}
    public record RoutePath(List<Coordinate> geometry, double distanceMeters, int estimatedDurationSeconds,
                            String provider, boolean generated) {}
}
