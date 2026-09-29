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
    public record RouteComparison(int availableRoutes, int rank, Double exposureDifferencePercent,
                                  Integer extraDurationSeconds, Double extraDistanceMeters) {}
    public record SelectionReason(String type, String headline, String summary, RouteComparison comparison,
                                  List<String> factors) {}
    public record CalculationRequest(Coordinate origin, Coordinate destination, TravelMode mode,
                                    RoutePreference preference, Instant departureAt) {}
    public record RouteAlternative(String alternativeId, int rank, String provider, boolean generated,
            List<Coordinate> geometry, double distanceMeters, int durationSeconds,
            Double expectedPollutionExposure, Integer forecastQualityScore,
            double preferenceScore, Map<String, Double> scoreComponents, List<String> reasons,
            String environmentalProvider, String environmentalCoverage, String observationSource, int sampledPointCount,
            int availableSampleCount, int unavailableSampleCount, Double environmentalCoveragePercent,
            SelectionReason selectionReason) {
        public RouteAlternative(String alternativeId, int rank, String provider, boolean generated,
                List<Coordinate> geometry, double distanceMeters, int durationSeconds,
                double expectedPollutionExposure, int forecastQualityScore, double preferenceScore,
                Map<String, Double> scoreComponents, List<String> reasons) {
            this(alternativeId, rank, provider, generated, geometry, distanceMeters, durationSeconds,
                    expectedPollutionExposure, forecastQualityScore, preferenceScore, scoreComponents, reasons,
                    "mock-demo-aqi", "fixed-cell", "historical-forecast", 0, 0, 0, null, null);
        }
    }
    public record CalculationResult(UUID id, Instant departureAt, TravelMode mode, RoutePreference preference,
            List<RouteAlternative> alternatives, boolean generated, String limitation) {}
}
