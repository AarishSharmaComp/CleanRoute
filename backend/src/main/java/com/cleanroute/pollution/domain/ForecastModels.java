package com.cleanroute.pollution.domain;

import java.time.Instant;
import java.util.List;

public final class ForecastModels {
    private ForecastModels() {}

    public record PollutionForecast(String cellId, Instant targetAt, Instant generatedAt,
            Integer aqi, Double pm25, Double pm10, Double no2, Double so2, Double co, Double o3,
            int qualityScore, String quality, int sampleCount, String provider, String modelVersion,
            boolean sourceGenerated) {}

    public record ForecastResponse(String dataType, boolean observed, boolean predicted,
            PollutionForecast forecast, String limitation) {}

    public record ForecastPrediction(Integer aqi, Double pm25, Double pm10, Double no2,
            Double so2, Double co, Double o3, int qualityScore, String quality,
            int sampleCount, String modelVersion, boolean sourceGenerated) {}

    public record ForecastHistoryResponse(String cellId, Instant from, Instant to,
            List<PollutionForecast> forecasts) {}
}
