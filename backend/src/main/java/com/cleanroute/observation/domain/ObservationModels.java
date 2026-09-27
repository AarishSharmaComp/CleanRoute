package com.cleanroute.observation.domain;

import java.time.Instant;

public final class ObservationModels {
    private ObservationModels() {}
    public record GeographicCell(String cellId, double latitude, double longitude) {}
    /** Concentrations are normalized to µg/m³, except CO in mg/m³; AQI retains its provider's index scale. */
    public record PollutionObservation(String cellId, Instant observedAt, Integer aqi, Double pm25, Double pm10,
            Double no2, Double so2, Double co, Double o3, String provider, boolean generated) {}
    public record WeatherObservation(String cellId, Instant observedAt, Double temperatureC, Double humidityPercent,
            Double windSpeedMps, Double windDirectionDegrees, Double precipitationMm, String condition,
            String provider, boolean generated) {}
    public record TrafficObservation(String cellId, Instant observedAt, String trafficLevel, Double congestionFactor,
            Double averageSpeedKph, String provider, boolean generated) {}
}
