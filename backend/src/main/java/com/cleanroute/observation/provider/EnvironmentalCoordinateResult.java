package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import java.util.List;

public record EnvironmentalCoordinateResult(EnvironmentalCoordinateQuery query,
                                            PollutionObservation observation, List<String> issues) {
    public EnvironmentalCoordinateResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public EnvironmentalCoordinateResult(EnvironmentalCoordinateQuery query, PollutionObservation observation) {
        this(query, observation, observation == null ? List.of("MISSING_MEASUREMENTS") : List.of());
    }
}
