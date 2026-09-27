package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.*;
import java.time.Instant;
import java.util.List;

public interface AQIProvider {
    String providerId();
    List<PollutionObservation> observations(GeographicCell cell, Instant timestamp);
}
