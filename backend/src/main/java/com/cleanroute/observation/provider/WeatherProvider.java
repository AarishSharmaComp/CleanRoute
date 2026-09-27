package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.*;
import java.time.Instant;

public interface WeatherProvider {
    String providerId();
    WeatherObservation observation(GeographicCell cell, Instant timestamp);
}
