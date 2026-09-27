package com.cleanroute.route.service;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
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
import com.cleanroute.route.repository.RouteCalculationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RouteService {
    private static final String LIMITATION = "Generated mock alternatives and nearest fixed demo-cell forecasts; exposure is a comparative baseline, not health guidance.";
    private final RoutingProvider routing;
    private final ObservationRepository observations;
    private final PollutionForecastService forecasts;
    private final PollutionEngine engine;
    private final RouteCalculationRepository calculations;
    private final ObjectMapper mapper;

    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                       PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper) {
        this.routing = routing; this.observations = observations; this.forecasts = forecasts;
        this.engine = engine; this.calculations = calculations; this.mapper = mapper;
    }

    public CalculationResult calculate(CalculationRequest request, UUID userId) {
        validate(request);
        if (!List.of(RoutePreference.FASTEST, RoutePreference.CLEANEST, RoutePreference.BALANCED).contains(request.preference()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Route preference must be FASTEST, CLEANEST, or BALANCED");
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
            Map<String, Double> components = Map.of("durationEfficiency", r.durationEfficiency(),
                    "pollutionCleanliness", 100 - c.exposure(), "expectedPollutionExposure", c.exposure());
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
        double weighted = 0, weights = 0;
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
            traversed += length;
        }
        return new Candidate(path, weights == 0 ? 0 : round(weighted / weights), quality);
    }

    private static GeographicCell nearest(Coordinate point, List<GeographicCell> cells) {
        return cells.stream().min(Comparator.comparingDouble(c -> haversine(point, new Coordinate(c.latitude(), c.longitude())))).orElseThrow();
    }

    private static Ranked rank(Candidate c, RoutePreference pref, double maxDuration) {
        double durationEfficiency = 100.0 * (1 - (double)c.path().estimatedDurationSeconds() / maxDuration);
        double cleanliness = 100 - c.exposure();
        double score = switch (pref) {
            case FASTEST -> durationEfficiency;
            case CLEANEST -> cleanliness;
            case BALANCED -> 0.5 * durationEfficiency + 0.5 * cleanliness;
            default -> throw new IllegalArgumentException("Unsupported route preference");
        };
        return new Ranked(c, durationEfficiency, score);
    }

    private static List<String> reasons(RoutePreference preference, Ranked ranked, Candidate candidate) {
        return switch (preference) {
            case FASTEST -> List.of("Ranked by estimated travel time.", "Pollution exposure is reported but does not change FASTEST ordering.");
            case CLEANEST -> List.of("Ranked by route-distance-weighted pollution exposures evaluated at estimated passage times.", "Travel time is reported but does not change CLEANEST ordering.");
            case BALANCED -> List.of("Balances normalized travel-time efficiency and pollution cleanliness equally.");
            default -> List.of();
        };
    }

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
    private record Candidate(RoutePath path, double exposure, int quality) {}
    private record Ranked(Candidate candidate, double durationEfficiency, double score) {}
}
