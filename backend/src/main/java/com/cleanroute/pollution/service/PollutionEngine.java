package com.cleanroute.pollution.service;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.pollution.domain.PollutionScoreModels.PollutantComponent;
import com.cleanroute.pollution.domain.PollutionScoreModels.PollutionAssessment;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Normalizes available measurements to a transparent 0..100 comparative burden proxy. */
@Service
public class PollutionEngine {
    private final PollutionScoringProperties properties;

    public PollutionEngine(PollutionScoringProperties properties) { this.properties = properties; }

    public PollutionAssessment assess(PollutionObservation observation) {
        if (observation == null) throw new IllegalArgumentException("Pollution observation is required");
        List<PollutantComponent> components = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        add(components, missing, "PM2.5", observation.pm25(), "µg/m³", properties.getPm25Reference());
        add(components, missing, "PM10", observation.pm10(), "µg/m³", properties.getPm10Reference());
        add(components, missing, "NO2", observation.no2(), "µg/m³", properties.getNo2Reference());
        add(components, missing, "SO2", observation.so2(), "µg/m³", properties.getSo2Reference());
        add(components, missing, "CO", observation.co(), "mg/m³", properties.getCoReference());
        add(components, missing, "O3", observation.o3(), "µg/m³", properties.getO3Reference());

        int available = (int) components.stream().filter(c -> c.score() != null).count();
        if (observation.aqi() != null && (observation.aqi() < 0 || observation.aqi() > properties.getAqiIndexMax()))
            throw new IllegalArgumentException("Invalid AQI value");
        boolean aqiFallback = available == 0 && observation.aqi() != null;
        if (available == 0 && !aqiFallback)
            throw new InsufficientPollutionDataException("No pollutant measurements or AQI are available for this interval");

        double burden = aqiFallback
                ? normalize(observation.aqi(), properties.getAqiIndexMax())
                : components.stream().filter(c -> c.score() != null).mapToDouble(PollutantComponent::score).average().orElseThrow();
        if (aqiFallback) components.add(new PollutantComponent("AQI", observation.aqi().doubleValue(),
                "provider index", properties.getAqiIndexMax(), burden, true));
        else if (observation.aqi() != null) components.add(new PollutantComponent("AQI", observation.aqi().doubleValue(),
                "provider index", properties.getAqiIndexMax(), normalize(observation.aqi(), properties.getAqiIndexMax()), false));
        return new PollutionAssessment(burden, available, (int) Math.round(available * 100.0 / 6), aqiFallback,
                List.copyOf(components), List.copyOf(missing), observation.provider(), observation.generated());
    }

    private static void add(List<PollutantComponent> components, List<String> missing, String name,
                            Double value, String unit, double reference) {
        if (value == null) {
            missing.add(name);
            components.add(new PollutantComponent(name, null, unit, reference, null, false));
            return;
        }
        if (!Double.isFinite(value) || value < 0)
            throw new IllegalArgumentException("Invalid measurement for " + name);
        components.add(new PollutantComponent(name, value, unit, reference, normalize(value, reference), true));
    }

    private static double normalize(double value, double reference) {
        return Math.max(0, Math.min(100, value / reference * 100));
    }

    public static class InsufficientPollutionDataException extends RuntimeException {
        public InsufficientPollutionDataException(String message) { super(message); }
    }
}
