package com.cleanroute.pollution.provider;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.domain.ForecastModels.ForecastPrediction;
import java.time.Instant;
import java.util.List;

/** Replaceable forecast interface. Implementations must return predictions, never observed values. */
public interface ForecastProvider {
    String providerId();
    ForecastPrediction predict(String cellId, Instant targetAt, List<PollutionObservation> history);
    class ForecastUnavailableException extends RuntimeException {}
}
