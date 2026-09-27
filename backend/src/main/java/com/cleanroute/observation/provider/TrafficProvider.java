package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.*;
import java.time.Instant;

public interface TrafficProvider {
    String providerId();
    TrafficObservation observation(GeographicCell cell, Instant timestamp);
}
