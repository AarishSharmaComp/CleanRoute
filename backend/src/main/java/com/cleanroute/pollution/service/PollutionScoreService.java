package com.cleanroute.pollution.service;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.domain.ObservationModels.TrafficObservation;
import com.cleanroute.observation.domain.ObservationModels.WeatherObservation;
import com.cleanroute.pollution.config.PollutionScoringProperties;
import com.cleanroute.pollution.domain.PollutionScoreModels.PollutionAssessment;
import com.cleanroute.pollution.domain.PollutionScoreModels.PollutionScore;
import com.cleanroute.pollution.domain.PollutionScoreModels.ScoreComponent;
import com.cleanroute.pollution.domain.PollutionScoreModels.PollutantComponent;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

/** Combines a pollutant burden proxy with interval and route-context inputs; it never ranks routes. */
@Service
public class PollutionScoreService {
    private static final double REFERENCE_DURATION_SECONDS = 1800.0;
    private static final double REFERENCE_DISTANCE_METERS = 5000.0;
    private final PollutionEngine engine;
    private final PollutionScoringProperties properties;

    public PollutionScoreService(PollutionEngine engine, PollutionScoringProperties properties) {
        this.engine = engine;
        this.properties = properties;
    }

    public PollutionScore score(PollutionObservation pollution, WeatherObservation weather,
                                TrafficObservation traffic, int durationSeconds,
                                double distanceMeters, TravelMode mode) {
        if (durationSeconds < 1 || !Double.isFinite(distanceMeters) || distanceMeters <= 0 || mode == null)
            throw new IllegalArgumentException("Positive duration, distance, and travel mode are required");
        PollutionAssessment assessment = engine.assess(pollution);
        double durationFactor = clamp(durationSeconds / REFERENCE_DURATION_SECONDS, 0, 2);
        double distanceFactor = clamp(distanceMeters / REFERENCE_DISTANCE_METERS, 0, 2);
        double modeFactor = modeFactor(mode);
        double routeExposure = clamp(assessment.burdenScore()
                * (durationFactor + distanceFactor) / 2.0 * modeFactor, 0, 100);

        List<UnweightedComponent> available = new ArrayList<>();
        available.add(new UnweightedComponent("pollutionExposure", routeExposure,
                properties.getPollutionWeight(), "Pollution burden scaled by duration, distance, and travel mode."));
        Double trafficScore = trafficScore(traffic);
        if (trafficScore != null) available.add(new UnweightedComponent("trafficContext", trafficScore,
                properties.getTrafficWeight(), "Congestion factor normalized from 1 (free-flow proxy) to 5 (highest demo congestion)."));
        Double weatherScore = weatherScore(weather);
        if (weatherScore != null) available.add(new UnweightedComponent("weatherContext", weatherScore,
                properties.getWeatherWeight(), "Low wind and precipitation form a simple dispersion-context proxy; missing fields are excluded."));

        double totalWeight = available.stream().mapToDouble(UnweightedComponent::weight).sum();
        if (!Double.isFinite(totalWeight) || totalWeight <= 0)
            throw new IllegalStateException("At least one positive scoring weight is required");
        List<ScoreComponent> components = available.stream().map(c -> new ScoreComponent(c.name(), c.score(),
                c.weight(), c.weight() / totalWeight, c.explanation())).toList();
        double total = components.stream().mapToDouble(c -> c.score() * c.appliedWeight()).sum();

        List<String> caveats = new ArrayList<>(List.of(
                "Comparative demo estimate only; not validated for health, medical, or route-selection decisions.",
                "Concentration reference values and component weights are configurable model baselines, not regulatory thresholds."));
        if (assessment.usedAqiFallback()) caveats.add("No individual pollutant concentrations were available; the provider AQI index was used as a fallback.");
        if (weatherScore == null) caveats.add("Weather context was unavailable and was excluded from the weighted score.");
        if (trafficScore == null) caveats.add("Traffic context was unavailable and was excluded from the weighted score.");
        if (assessment.pollutantCoveragePercent() < 100 && !assessment.usedAqiFallback())
            caveats.add("Missing pollutant measurements were excluded; they were not treated as zero.");
        boolean generated = pollution.generated() || weather != null && weather.generated() || traffic != null && traffic.generated();
        if (generated) caveats.add("One or more input observations are generated demo data.");

        return new PollutionScore(pollution.cellId(), pollution.observedAt(), round(total),
                "Higher means greater modeled comparative burden; this is not a route ranking.",
                "Available pollutant reference ratios are averaged, then combined with duration/distance/mode exposure and available traffic/weather context using configured weights.",
                round(assessment.burdenScore()), round(durationFactor), round(distanceFactor), modeFactor,
                components, assessment.pollutants(), assessment.pollutantCoveragePercent(),
                assessment.missingPollutants(), assessment.provider(), weather == null ? null : weather.provider(),
                traffic == null ? null : traffic.provider(), generated, List.copyOf(caveats));
    }

    private Double trafficScore(TrafficObservation traffic) {
        if (traffic == null || traffic.congestionFactor() == null) return null;
        double factor = traffic.congestionFactor();
        if (!Double.isFinite(factor) || factor < 1 || factor > 5) throw new IllegalArgumentException("Invalid traffic congestion factor");
        return (factor - 1) / 4 * 100;
    }

    private Double weatherScore(WeatherObservation weather) {
        if (weather == null) return null;
        List<Double> available = new ArrayList<>();
        if (weather.windSpeedMps() != null) {
            double wind = weather.windSpeedMps();
            if (!Double.isFinite(wind) || wind < 0) throw new IllegalArgumentException("Invalid wind speed");
            available.add(100 * (1 - clamp(wind / properties.getWindReferenceMps(), 0, 1)));
        }
        if (weather.precipitationMm() != null) {
            double precipitation = weather.precipitationMm();
            if (!Double.isFinite(precipitation) || precipitation < 0) throw new IllegalArgumentException("Invalid precipitation");
            available.add(100 / (1 + precipitation));
        }
        return available.isEmpty() ? null : available.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static double modeFactor(TravelMode mode) {
        return switch (mode) { case CAR -> 0.8; case WALK -> 1.0; case CYCLE -> 1.2; case JOG -> 1.5; };
    }

    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double round(double value) { return Math.round(value * 100.0) / 100.0; }
    private record UnweightedComponent(String name, double score, double weight, String explanation) {}
}
