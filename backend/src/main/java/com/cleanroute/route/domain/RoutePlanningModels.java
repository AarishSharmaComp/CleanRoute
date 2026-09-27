package com.cleanroute.route.domain;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RoutePlanningModels {
    private RoutePlanningModels() {}
    public record CalculationRequest(Coordinate origin, Coordinate destination, TravelMode mode,
                                    RoutePreference preference, Instant departureAt) {}
    public record RouteAlternative(String alternativeId, int rank, String provider, boolean generated,
            List<Coordinate> geometry, double distanceMeters, int durationSeconds,
            double expectedPollutionExposure, int forecastQualityScore,
            double preferenceScore, Map<String, Double> scoreComponents, List<String> reasons) {}
    public record CalculationResult(UUID id, Instant departureAt, TravelMode mode, RoutePreference preference,
            List<RouteAlternative> alternatives, boolean generated, String limitation) {}
}
