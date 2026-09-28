package com.cleanroute.observation.domain;

import com.cleanroute.domain.TravelMode;
import java.util.List;

public final class RoutingModels {
    private RoutingModels() {}
    public record Coordinate(double latitude, double longitude) {}
    public record RoutingRequest(Coordinate origin, Coordinate destination, TravelMode mode) {
        public RoutingRequest(Coordinate origin, Coordinate destination) { this(origin, destination, TravelMode.CAR); }
    }
    public record RoutePath(List<Coordinate> geometry, double distanceMeters, int estimatedDurationSeconds,
                            String provider, boolean generated, String alternativeId,
                            Double greenAreaCoverage, Boolean cyclingCompatible, Double elevationGainMeters) {
        public RoutePath(List<Coordinate> geometry, double distanceMeters, int estimatedDurationSeconds,
                         String provider, boolean generated, String alternativeId) {
            this(geometry, distanceMeters, estimatedDurationSeconds, provider, generated, alternativeId, null, null, null);
        }
        public RoutePath(List<Coordinate> geometry, double distanceMeters, int estimatedDurationSeconds,
                         String provider, boolean generated) {
            this(geometry, distanceMeters, estimatedDurationSeconds, provider, generated, "default", null, null, null);
        }
    }
}
