package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;

public record EnvironmentalCoordinateResult(EnvironmentalCoordinateQuery query,
                                            PollutionObservation observation) {
}
