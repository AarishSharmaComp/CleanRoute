package com.cleanroute.route.service;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.domain.ObservationModels.TrafficObservation;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.RoutingProvider;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionForecastService;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationRequest;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationResult;
import com.cleanroute.route.domain.RoutePlanningModels.RouteAlternative;
import com.cleanroute.route.config.RouteSuitabilityProperties;
import com.cleanroute.route.repository.RouteCalculationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Locale;

@Service
public class RouteService {
    private static final String LIMITATION = "Generated mock alternatives and nearest fixed demo-cell forecasts; exposure is a comparative baseline, not health guidance.";
    private final RoutingProvider routing;
    private final ObservationRepository observations;
    private final PollutionForecastService forecasts;
    private final PollutionEngine engine;
    private final RouteCalculationRepository calculations;
    private final ObjectMapper mapper;
    private final RouteSuitabilityProperties suitability;

    @Autowired
    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                       PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper,
                       RouteSuitabilityProperties suitability) {
        this.routing = routing; this.observations = observations; this.forecasts = forecasts;
        this.engine = engine; this.calculations = calculations; this.mapper = mapper; this.suitability = suitability;
    }

    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                       PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper) {
        this(routing, observations, forecasts, engine, calculations, mapper, new RouteSuitabilityProperties());
    }

    @Transactional
    public CalculationResult calculate(CalculationRequest request, UUID userId) {
        validate(request);
        if (!List.of(RoutePreference.FASTEST, RoutePreference.CLEANEST, RoutePreference.BALANCED,
                RoutePreference.JOGGER, RoutePreference.CYCLIST).contains(request.preference()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported route preference");
        if (request.preference() == RoutePreference.JOGGER && request.mode() != TravelMode.JOG
                || request.preference() == RoutePreference.CYCLIST && request.mode() != TravelMode.CYCLE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JOGGER requires JOG mode and CYCLIST requires CYCLE mode");
        Instant departure = request.departureAt() == null ? ceilQuarter(Instant.now().plusSeconds(1)) : request.departureAt();
        if (!departure.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Departure time must be in the future");
        List<GeographicCell> cells = observations.cells();
        if (cells.isEmpty()) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "No geographic cells are configured");
        List<RoutePath> paths = routing.alternatives(new RoutingRequest(request.origin(), request.destination(), request.mode()));
        if (paths == null || paths.isEmpty() || paths.size() > 10 || paths.stream().anyMatch(java.util.Objects::isNull))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Routing provider returned no usable alternatives");
        List<Candidate> candidates = new ArrayList<>();
        Map<String, PollutionForecast> forecastCache = new HashMap<>();
        for (RoutePath path : paths) candidates.add(exposure(path, departure, cells, forecastCache));
        double maxDuration = candidates.stream().mapToInt(c -> c.path().estimatedDurationSeconds()).max().orElse(1);
        List<Ranked> ranked = candidates.stream().map(c -> rank(c, request.preference(), maxDuration))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed().thenComparing(r -> r.candidate().path().alternativeId())).toList();
        List<RouteAlternative> results = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            Ranked r = ranked.get(i); Candidate c = r.candidate();
            Map<String, Double> components = components(r, c, request.preference());
            results.add(new RouteAlternative(c.path().alternativeId(), i + 1, c.path().provider(), c.path().generated(),
                    c.path().geometry(), c.path().distanceMeters(), c.path().estimatedDurationSeconds(), c.exposure(),
                    c.quality(), round(r.score()), components, reasons(request.preference(), r, c)));
        }
        CalculationResult result = new CalculationResult(UUID.randomUUID(), departure, request.mode(), request.preference(),
                List.copyOf(results), paths.stream().anyMatch(RoutePath::generated), LIMITATION);
        try {
            calculations.save(result.id(), userId, request.origin().latitude(), request.origin().longitude(),
                    request.destination().latitude(), request.destination().longitude(), request.mode().name(),
                    request.preference().name(), mapper.writeValueAsString(result));
            RouteAlternative selected = results.getFirst();
            calculations.saveHistory(userId, coordinateName(request.origin()), coordinateName(request.destination()),
                    request.mode().name(), request.preference().name(), selected.expectedPollutionExposure(),
                    selected.durationSeconds(), selected.distanceMeters());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize route calculation", e);
        }
        return result;
    }

    public JsonNode get(UUID id, UUID userId) {
        return calculations.find(id, userId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Route calculation not found"));
    }

    private Candidate exposure(RoutePath path, Instant departure, List<GeographicCell> cells,
                              Map<String, PollutionForecast> forecastCache) {
        if (path.geometry() == null || path.geometry().size() < 2 || !Double.isFinite(path.distanceMeters())
                || path.distanceMeters() <= 0 || path.estimatedDurationSeconds() < 1 || path.geometry().size() > 500)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Routing provider returned an invalid route");
        if (path.greenAreaCoverage() != null && (!Double.isFinite(path.greenAreaCoverage())
                || path.greenAreaCoverage() < 0 || path.greenAreaCoverage() > 1)
                || path.elevationGainMeters() != null && (!Double.isFinite(path.elevationGainMeters())
                || path.elevationGainMeters() < 0))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Routing provider returned invalid suitability metadata");
        double weighted = 0, weights = 0, congestionWeighted = 0, trafficWeights = 0;
        int quality = 100;
        List<Coordinate> points = path.geometry();
        if (points.stream().anyMatch(p -> p == null || !Double.isFinite(p.latitude()) || p.latitude() < -90 || p.latitude() > 90
                || !Double.isFinite(p.longitude()) || p.longitude() < -180 || p.longitude() > 180))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Routing provider returned invalid geometry");
        double totalGeometryDistance = 0;
        List<Double> legs = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            double length = haversine(points.get(i - 1), points.get(i)); legs.add(length); totalGeometryDistance += length;
        }
        double traversed = 0;
        for (int i = 0; i < legs.size(); i++) {
            double length = legs.get(i);
            Coordinate a = points.get(i), b = points.get(i + 1);
            Coordinate midpoint = new Coordinate((a.latitude() + b.latitude()) / 2, (a.longitude() + b.longitude()) / 2);
            GeographicCell cell = nearest(midpoint, cells);
            double passageFraction = (traversed + length / 2) / Math.max(1, totalGeometryDistance);
            Instant target = ceilQuarter(departure.plusMillis((long)(path.estimatedDurationSeconds() * 1000.0 * passageFraction)));
            PollutionForecast forecast = forecastCache.computeIfAbsent(cell.cellId() + "|" + target,
                    ignored -> forecasts.forecast(cell.cellId(), target));
            PollutionObservation observation = new PollutionObservation(cell.cellId(), forecast.targetAt(), forecast.aqi(),
                    forecast.pm25(), forecast.pm10(), forecast.no2(), forecast.so2(), forecast.co(), forecast.o3(),
                    forecast.provider(), forecast.sourceGenerated());
            double burden = engine.assess(observation).burdenScore();
            double weight = length > 0 ? length : 1;
            weighted += burden * weight; weights += weight;
            quality = Math.min(quality, forecast.qualityScore());
            TrafficObservation traffic = observations.trafficAt(cell.cellId(), target).orElse(null);
            if (traffic != null && traffic.congestionFactor() != null && Double.isFinite(traffic.congestionFactor())
                    && traffic.congestionFactor() >= 0) {
                congestionWeighted += traffic.congestionFactor() * weight;
                trafficWeights += weight;
            }
            traversed += length;
        }
        return new Candidate(path, weights == 0 ? 0 : round(weighted / weights), quality,
                trafficWeights == 0 ? null : congestionWeighted / trafficWeights);
    }

    private static GeographicCell nearest(Coordinate point, List<GeographicCell> cells) {
        return cells.stream().min(Comparator.comparingDouble(c -> haversine(point, new Coordinate(c.latitude(), c.longitude())))).orElseThrow();
    }

    private Ranked rank(Candidate c, RoutePreference pref, double maxDuration) {
        double durationEfficiency = 100.0 * (1 - (double)c.path().estimatedDurationSeconds() / maxDuration);
        double cleanliness = clamp(100 - c.exposure());
        Double trafficComfort = c.congestionFactor() == null ? null : thresholdScore(c.congestionFactor(),
                pref == RoutePreference.CYCLIST ? suitability.getCyclistMaximumCongestionFactor()
                        : suitability.getJoggerMaximumCongestionFactor(), 25);
        Double distanceSuitability = distanceSuitability(c.path().distanceMeters());
        Double cyclingCompatibility = c.path().cyclingCompatible() == null ? null : c.path().cyclingCompatible() ? 100.0 : 0.0;
        Double elevationSuitability = c.path().elevationGainMeters() == null ? null
                : clamp(100 * (1 - c.path().elevationGainMeters() / suitability.getMaximumElevationGainMeters()));
        Double greenAreaPreference = c.path().greenAreaCoverage() == null ? null : clamp(100 * c.path().greenAreaCoverage());
        double score = switch (pref) {
            case FASTEST -> durationEfficiency;
            case CLEANEST -> cleanliness;
            case BALANCED -> 0.5 * durationEfficiency + 0.5 * cleanliness;
            case JOGGER -> weightedScore(
                    new double[]{thresholdScore(c.exposure(), suitability.getJoggerMaximumPollutionExposure(), 2),
                            trafficComfort == null ? 0 : trafficComfort, distanceSuitability,
                            greenAreaPreference == null ? 0 : greenAreaPreference},
                    new double[]{suitability.getPollutionWeight(), suitability.getTrafficWeight(),
                            suitability.getDistanceWeight(), 0.05},
                    new boolean[]{true, trafficComfort != null, true,
                            suitability.isPreferGreenAreas() && greenAreaPreference != null});
            case CYCLIST -> weightedScore(
                    new double[]{thresholdScore(c.exposure(), suitability.getCyclistMaximumPollutionExposure(), 2), trafficComfort == null ? 0 : trafficComfort,
                            cyclingCompatibility == null ? 0 : cyclingCompatibility,
                            elevationSuitability == null ? 0 : elevationSuitability,
                            greenAreaPreference == null ? 0 : greenAreaPreference},
                    new double[]{0.45, 0.30, 0.10, 0.10, 0.05},
                    new boolean[]{true, trafficComfort != null, cyclingCompatibility != null,
                            elevationSuitability != null, suitability.isPreferGreenAreas() && greenAreaPreference != null});
            default -> throw new IllegalArgumentException("Unsupported route preference");
        };
        return new Ranked(c, durationEfficiency, score, trafficComfort, distanceSuitability,
                cyclingCompatibility, elevationSuitability, greenAreaPreference);
    }

    private List<String> reasons(RoutePreference preference, Ranked ranked, Candidate candidate) {
        return switch (preference) {
            case FASTEST -> List.of("Ranked by estimated travel time.", "Pollution exposure is reported but does not change FASTEST ordering.");
            case CLEANEST -> List.of("Ranked by route-distance-weighted pollution exposures evaluated at estimated passage times.", "Travel time is reported but does not change CLEANEST ordering.");
            case BALANCED -> List.of("Balances normalized travel-time efficiency and pollution cleanliness equally.");
            case JOGGER -> {
                List<String> reasons = new ArrayList<>(List.of("Ranks lower-pollution, lower-traffic routes and favors distances around the configured jogger range."));
                if (candidate.congestionFactor() == null) reasons.add("Traffic data is unavailable and was excluded from this score.");
                if (!suitability.isPreferGreenAreas()) reasons.add("Green-area preference is disabled and its metadata was not used.");
                else if (candidate.path().greenAreaCoverage() == null) reasons.add("Green-area metadata is unavailable and was not used.");
                else reasons.add("Green-area coverage metadata was used to favor routes with more green-area coverage.");
                yield List.copyOf(reasons);
            }
            case CYCLIST -> {
                List<String> reasons = new ArrayList<>(List.of("Ranks lower-pollution and lower-traffic routes."));
                if (candidate.congestionFactor() == null) reasons.add("Traffic data is unavailable and was excluded from this score.");
                if (candidate.path().cyclingCompatible() == null) reasons.add("Cycling-compatibility metadata is unavailable and was not used.");
                if (candidate.path().elevationGainMeters() == null) reasons.add("Elevation metadata is unavailable and was not used.");
                if (suitability.isPreferGreenAreas() && candidate.path().greenAreaCoverage() == null)
                    reasons.add("Green-area metadata is unavailable and was not used.");
                yield List.copyOf(reasons);
            }
        };
    }

    private Map<String, Double> components(Ranked ranked, Candidate candidate, RoutePreference preference) {
        Map<String, Double> values = new java.util.LinkedHashMap<>();
        values.put("durationEfficiency", ranked.durationEfficiency());
        values.put("pollutionCleanliness", clamp(100 - candidate.exposure()));
        values.put("expectedPollutionExposure", candidate.exposure());
        if (ranked.trafficComfort() != null) values.put("trafficComfort", ranked.trafficComfort());
        if (preference == RoutePreference.JOGGER) values.put("distanceSuitability", ranked.distanceSuitability());
        if (ranked.cyclingCompatibility() != null) values.put("cyclingCompatibility", ranked.cyclingCompatibility());
        if (ranked.elevationSuitability() != null) values.put("elevationSuitability", ranked.elevationSuitability());
        if (suitability.isPreferGreenAreas() && ranked.greenAreaPreference() != null)
            values.put("greenAreaPreference", ranked.greenAreaPreference());
        return Map.copyOf(values);
    }

    private double distanceSuitability(double distance) {
        double min = suitability.getJoggerMinimumDistanceMeters();
        double ideal = Math.max(min, suitability.getJoggerIdealMaximumDistanceMeters());
        double max = Math.max(ideal + 1, suitability.getJoggerMaximumDistanceMeters());
        if (distance < min) return clamp(100 * distance / min);
        if (distance <= ideal) return 100;
        return clamp(100 * (max - distance) / (max - ideal));
    }

    private static double thresholdScore(double value, double threshold, double declinePerUnit) {
        double base = threshold <= 0 ? 0 : 100 - value / threshold * 25;
        return clamp(base - Math.max(0, value - threshold) * declinePerUnit);
    }

    private static double weightedScore(double[] values, double[] weights, boolean[] available) {
        double score = 0, totalWeight = 0;
        for (int i = 0; i < values.length; i++) if (available[i] && weights[i] > 0) {
            score += values[i] * weights[i]; totalWeight += weights[i];
        }
        return totalWeight == 0 ? 0 : score / totalWeight;
    }

    private static double clamp(double value) { return Math.max(0, Math.min(100, value)); }

    private static void validate(CalculationRequest r) {
        if (r == null || r.origin() == null || r.destination() == null || r.mode() == null || r.preference() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origin, destination, mode, and preference are required");
        for (Coordinate c : List.of(r.origin(), r.destination()))
            if (!Double.isFinite(c.latitude()) || c.latitude() < -90 || c.latitude() > 90
                    || !Double.isFinite(c.longitude()) || c.longitude() < -180 || c.longitude() > 180)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Coordinates are outside valid latitude/longitude ranges");
    }

    private static Instant ceilQuarter(Instant time) {
        long floor = Math.floorDiv(time.getEpochSecond(), 900) * 900;
        return Instant.ofEpochSecond(time.getEpochSecond() == floor ? floor : floor + 900);
    }
    private static double haversine(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
    private static double round(double n) { return Math.round(n * 100.0) / 100.0; }
    private static String coordinateName(Coordinate coordinate) {
        return String.format(Locale.ROOT, "%.5f, %.5f", coordinate.latitude(), coordinate.longitude());
    }
    private record Candidate(RoutePath path, double exposure, int quality, Double congestionFactor) {}
    private record Ranked(Candidate candidate, double durationEfficiency, double score, Double trafficComfort,
                          Double distanceSuitability, Double cyclingCompatibility, Double elevationSuitability,
                          Double greenAreaPreference) {}
}
