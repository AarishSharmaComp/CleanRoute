package com.cleanroute.pollution.provider;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.domain.ForecastModels.ForecastPrediction;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;

/** Deterministic 15-minute weekday/time historical baseline with exponential recency weighting. */
@Component
public class HistoricalAverageForecastProvider implements ForecastProvider {
    private static final String VERSION = "weekday-slot-weighted-v1";

    @Override public String providerId() { return "historical-average"; }

    @Override public ForecastPrediction predict(String cellId, Instant targetAt, List<PollutionObservation> history) {
        if (targetAt == null || Math.floorMod(targetAt.getEpochSecond(), 900) != 0)
            throw new IllegalArgumentException("Forecast target must align to a 15-minute boundary");
        List<PollutionObservation> valid = history == null ? List.of() : history.stream()
                .filter(o -> o != null && cellId.equals(o.cellId()) && o.observedAt() != null
                        && !o.observedAt().isAfter(targetAt) && hasMeasurement(o))
                .toList();
        if (valid.isEmpty()) throw new ForecastUnavailableException();
        valid = aggregateIntervals(valid);

        var target = targetAt.atZone(ZoneOffset.UTC);
        var sameWeekSlot = valid.stream().filter(o -> {
            var t = o.observedAt().atZone(ZoneOffset.UTC);
            return t.getDayOfWeek() == target.getDayOfWeek()
                    && t.getHour() == target.getHour() && t.getMinute() == target.getMinute();
        }).toList();
        var sameDaySlot = valid.stream().filter(o -> {
            var t = o.observedAt().atZone(ZoneOffset.UTC);
            return t.getHour() == target.getHour() && t.getMinute() == target.getMinute();
        }).toList();
        List<PollutionObservation> selected = sameWeekSlot.isEmpty()
                ? (sameDaySlot.isEmpty() ? valid : sameDaySlot) : sameWeekSlot;
        boolean exactPattern = !sameWeekSlot.isEmpty();
        double sampleCoverage = Math.min(1.0, selected.size() / 4.0);
        double recency = selected.stream().mapToDouble(o -> Math.exp(-Math.max(0,
                Duration.between(o.observedAt(), targetAt).toSeconds()) / (60.0 * 86400.0))).average().orElse(0);
        int qualityScore = (int) Math.round(100 * sampleCoverage * recency * (exactPattern ? 1.0 : 0.65));
        String quality = qualityScore >= 75 ? "HIGH" : qualityScore >= 40 ? "MEDIUM" : "LOW";
        return new ForecastPrediction(
                weightedInteger(selected, targetAt, PollutionObservation::aqi),
                weightedMean(selected, targetAt, PollutionObservation::pm25),
                weightedMean(selected, targetAt, PollutionObservation::pm10),
                weightedMean(selected, targetAt, PollutionObservation::no2),
                weightedMean(selected, targetAt, PollutionObservation::so2),
                weightedMean(selected, targetAt, PollutionObservation::co),
                weightedMean(selected, targetAt, PollutionObservation::o3), qualityScore, quality,
                selected.size(), VERSION, selected.stream().anyMatch(PollutionObservation::generated));
    }

    private static Integer weightedInteger(List<PollutionObservation> rows,
                                           Instant targetAt, java.util.function.Function<PollutionObservation, Integer> value) {
        Double result = weightedMean(rows, targetAt, row -> value.apply(row) == null ? null : value.apply(row).doubleValue());
        return result == null ? null : (int) Math.round(result);
    }

    private static boolean hasMeasurement(PollutionObservation o) {
        if (o.aqi() != null && o.aqi() >= 0 && o.aqi() <= 500) return true;
        return java.util.stream.Stream.of(o.pm25(), o.pm10(), o.no2(), o.so2(), o.co(), o.o3())
                .anyMatch(v -> v != null && Double.isFinite(v) && v >= 0);
    }

    private static List<PollutionObservation> aggregateIntervals(List<PollutionObservation> observations) {
        Map<Instant, List<PollutionObservation>> byTimestamp = new TreeMap<>();
        observations.forEach(o -> byTimestamp.computeIfAbsent(o.observedAt(), ignored -> new java.util.ArrayList<>()).add(o));
        return byTimestamp.entrySet().stream().map(entry -> {
            List<PollutionObservation> rows = entry.getValue();
            rows.sort(Comparator.comparing(o -> o.provider() == null ? "" : o.provider()));
            Integer aqi = average(rows, o -> o.aqi() == null || o.aqi() < 0 || o.aqi() > 500 ? null : o.aqi().doubleValue())
                    .map(Math::round).map(Long::intValue).orElse(null);
            return new PollutionObservation(rows.getFirst().cellId(), entry.getKey(), aqi,
                    average(rows, PollutionObservation::pm25).orElse(null),
                    average(rows, PollutionObservation::pm10).orElse(null),
                    average(rows, PollutionObservation::no2).orElse(null),
                    average(rows, PollutionObservation::so2).orElse(null),
                    average(rows, PollutionObservation::co).orElse(null),
                    average(rows, PollutionObservation::o3).orElse(null),
                    "historical-interval-average", rows.stream().anyMatch(PollutionObservation::generated));
        }).toList();
    }

    private static java.util.Optional<Double> average(List<PollutionObservation> rows,
            java.util.function.Function<PollutionObservation, Double> value) {
        double mean = 0;
        int count = 0;
        for (PollutionObservation row : rows) {
            Double measurement = value.apply(row);
            if (measurement == null || !Double.isFinite(measurement) || measurement < 0) continue;
            mean += (measurement - mean) / ++count;
        }
        return count == 0 ? java.util.Optional.empty() : java.util.Optional.of(mean);
    }

    private static Double weightedMean(List<PollutionObservation> rows, Instant targetAt,
                                       java.util.function.Function<PollutionObservation, Double> value) {
        double mean = 0, weights = 0;
        for (PollutionObservation row : rows) {
            Double measurement = value.apply(row);
            if (measurement == null || !Double.isFinite(measurement) || measurement < 0) continue;
            double ageDays = Math.max(0, Duration.between(row.observedAt(), targetAt).toSeconds() / 86400.0);
            double weight = Math.exp(-ageDays / 60.0);
            double updatedWeight = weights + weight;
            mean += (measurement - mean) * (weight / updatedWeight);
            weights = updatedWeight;
        }
        return weights == 0 ? null : mean;
    }

}
