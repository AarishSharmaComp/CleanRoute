package com.cleanroute.observation.service;

import com.cleanroute.observation.domain.ObservationModels.*;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

/** Validates provider output and aligns observation times to CleanRoute's 15-minute buckets. */
public final class ObservationNormalizer {
    public static final Duration FUTURE_CLOCK_SKEW_TOLERANCE = Duration.ofMinutes(2);
    private ObservationNormalizer() {}
    public static PollutionObservation pollution(PollutionObservation o, String providerId, String cellId, Instant now) {
        if (o == null || !identityMatches(o.provider(), o.cellId(), providerId, cellId) || o.observedAt() == null || tooFarInFuture(o.observedAt(), now) ||
                (o.aqi() == null && o.pm25() == null && o.pm10() == null && o.no2() == null && o.so2() == null && o.co() == null && o.o3() == null) ||
                bad(o.pm25()) || bad(o.pm10()) || bad(o.no2()) || bad(o.so2()) || bad(o.co()) || bad(o.o3()) ||
                o.aqi() != null && (o.aqi() < 0 || o.aqi() > 500)) throw new IllegalArgumentException("Invalid pollution provider response");
        return new PollutionObservation(o.cellId(), quarter(o.observedAt()), o.aqi(), o.pm25(), o.pm10(), o.no2(), o.so2(), o.co(), o.o3(), o.provider(), o.generated());
    }
    public static WeatherObservation weather(WeatherObservation o, String providerId, String cellId, Instant now) {
        if (o == null || !identityMatches(o.provider(), o.cellId(), providerId, cellId) || o.observedAt() == null || tooFarInFuture(o.observedAt(), now) ||
                o.temperatureC() == null && o.humidityPercent() == null && o.windSpeedMps() == null && o.windDirectionDegrees() == null && o.precipitationMm() == null && o.condition() == null ||
                !finite(o.temperatureC()) || bad(o.humidityPercent()) || o.humidityPercent() != null && o.humidityPercent() > 100 || bad(o.windSpeedMps()) || bad(o.precipitationMm()) ||
                o.windDirectionDegrees() != null && (!Double.isFinite(o.windDirectionDegrees()) || o.windDirectionDegrees() < 0 || o.windDirectionDegrees() > 360)) throw new IllegalArgumentException("Invalid weather provider response");
        return new WeatherObservation(o.cellId(), quarter(o.observedAt()), o.temperatureC(), o.humidityPercent(), o.windSpeedMps(), o.windDirectionDegrees(), o.precipitationMm(), o.condition(), o.provider(), o.generated());
    }
    public static TrafficObservation traffic(TrafficObservation o, String providerId, String cellId, Instant now) {
        if (o == null || !identityMatches(o.provider(), o.cellId(), providerId, cellId) || o.observedAt() == null || tooFarInFuture(o.observedAt(), now) ||
                o.trafficLevel() == null && o.congestionFactor() == null && o.averageSpeedKph() == null ||
                bad(o.averageSpeedKph()) || o.congestionFactor() != null && (!Double.isFinite(o.congestionFactor()) || o.congestionFactor() < 1 || o.congestionFactor() > 5)) throw new IllegalArgumentException("Invalid traffic provider response");
        return new TrafficObservation(o.cellId(), quarter(o.observedAt()), o.trafficLevel(), o.congestionFactor(), o.averageSpeedKph(), o.provider(), o.generated());
    }
    private static boolean identityMatches(String responseProvider, String responseCell, String expectedProvider, String expectedCell) {
        return responseProvider != null && !responseProvider.isBlank() && responseCell != null && !responseCell.isBlank() &&
                responseProvider.equals(expectedProvider) && responseCell.equals(expectedCell);
    }
    private static boolean tooFarInFuture(Instant observedAt, Instant now) {
        return now == null || observedAt.isAfter(now.plus(FUTURE_CLOCK_SKEW_TOLERANCE));
    }
    private static boolean finite(Double value) { return value == null || Double.isFinite(value); }
    private static boolean bad(Double value) { return value != null && (!Double.isFinite(value) || value < 0); }
    private static Instant quarter(Instant t) { return Instant.ofEpochSecond(Math.floorDiv(t.getEpochSecond(), 900) * 900); }
}
