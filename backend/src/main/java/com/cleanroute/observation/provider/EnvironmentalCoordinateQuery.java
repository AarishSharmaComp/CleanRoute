package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import java.time.Instant;

public record EnvironmentalCoordinateQuery(Coordinate coordinate, Instant observedAt) {
}
