package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import java.time.Instant;
import java.util.List;

/** Provider-neutral source of normalized pollution observations. */
public interface EnvironmentalDataProvider {
    String providerId();
    List<PollutionObservation> observations(GeographicCell cell, Instant timestamp);

    /** Real-time providers cannot safely replay a requested historical timestamp. */
    default boolean supportsHistoricalIngestion() { return true; }

    default boolean supportsCoordinateLookup() { return false; }

    default List<EnvironmentalCoordinateResult> observationsAt(List<EnvironmentalCoordinateQuery> queries) {
        throw ProviderFailureException.temporary();
    }
}
