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
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.provider.EnvironmentalDataProvider;
import com.cleanroute.observation.provider.EnvironmentalCoordinateQuery;
import com.cleanroute.observation.provider.EnvironmentalCoordinateResult;
import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.observation.config.RoutingProperties;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionForecastService;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationRequest;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationResult;
import com.cleanroute.route.domain.RoutePlanningModels.RouteAlternative;
import com.cleanroute.route.domain.RoutePlanningModels.RouteComparison;
import com.cleanroute.route.domain.RoutePlanningModels.SelectionReason;
import com.cleanroute.route.domain.EnvironmentalQuality;
import com.cleanroute.observation.domain.ObservationModels;
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
import java.util.Optional;
import java.util.Set;

@Service
public class RouteService {
    private static final String EXPOSURE_LIMITATION = " Exposure is a distance-weighted mean pollutant burden, not cumulative dose or health guidance. Sample availability is not model accuracy; coarse model grids and fixed demo cells are not street-level measurements.";
    private static final String MOCK_LIMITATION = "Generated mock route geometry; environmental provenance is reported separately." + EXPOSURE_LIMITATION;
    private static final String REAL_ROUTE_LIMITATION = "Road geometry and travel estimates come from the configured routing provider." + EXPOSURE_LIMITATION;
    private final RoutingProvider routing;
    private final ObservationRepository observations;
    private final PollutionForecastService forecasts;
    private final PollutionEngine engine;
    private final RouteCalculationRepository calculations;
    private final ObjectMapper mapper;
    private final RouteSuitabilityProperties suitability;
    private final EnvironmentalDataProvider environmental;
    private final EnvironmentalProperties environmentalProperties;
    private final RoutingProperties routingProperties;

    @Autowired
    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                       PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper,
                       RouteSuitabilityProperties suitability, EnvironmentalDataProvider environmental,
                       EnvironmentalProperties environmentalProperties, RoutingProperties routingProperties) {
        this.routing = routing; this.observations = observations; this.forecasts = forecasts;
        this.engine = engine; this.calculations = calculations; this.mapper = mapper; this.suitability = suitability;
        this.environmental = environmental; this.environmentalProperties = environmentalProperties;
        this.routingProperties = routingProperties;
    }

    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                       PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper) {
        this(routing, observations, forecasts, engine, calculations, mapper, new RouteSuitabilityProperties(), null, new EnvironmentalProperties(), new RoutingProperties());
    }

    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                        PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper,
                        RouteSuitabilityProperties suitability) {
        this(routing, observations, forecasts, engine, calculations, mapper, suitability, null, new EnvironmentalProperties(), new RoutingProperties());
    }

    public RouteService(RoutingProvider routing, ObservationRepository observations, PollutionForecastService forecasts,
                        PollutionEngine engine, RouteCalculationRepository calculations, ObjectMapper mapper,
                        RouteSuitabilityProperties suitability, EnvironmentalDataProvider environmental,
                        EnvironmentalProperties environmentalProperties) {
        this(routing, observations, forecasts, engine, calculations, mapper, suitability, environmental,
                environmentalProperties, new RoutingProperties());
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
        List<RoutePath> paths;
        try {
            paths = routing.alternatives(new RoutingRequest(request.origin(), request.destination(), request.mode()));
        } catch (ProviderFailureException failure) {
            if (failure.getType() == ProviderFailureException.Type.UNSUPPORTED)
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "The configured routing provider does not support " + request.mode().name().toLowerCase(Locale.ROOT) + " routing.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Routing provider is temporarily unavailable");
        }
        if (paths == null || paths.isEmpty() || paths.size() > 10 || paths.stream().anyMatch(java.util.Objects::isNull))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Routing provider returned no usable alternatives");
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Optional<PollutionForecast>> forecastCache = new HashMap<>();
        for (RoutePath path : paths) candidates.add(exposure(path, departure, cells, forecastCache));
        boolean comparable = EnvironmentalQuality.comparable(candidates.stream().map(Candidate::evidence).toList());
        double maxDuration = candidates.stream().mapToInt(c -> c.path().estimatedDurationSeconds()).max().orElse(1);
        List<Ranked> ranked = candidates.stream().map(c -> rank(c, request.preference(), maxDuration, comparable))
                .sorted(Comparator.comparingDouble(Ranked::score).reversed()
                        .thenComparingInt(r -> r.candidate().path().estimatedDurationSeconds())
                        .thenComparing(r -> r.candidate().path().alternativeId())).toList();
        List<RouteAlternative> results = new ArrayList<>();
        Candidate fastest = candidates.stream().min(Comparator.comparingInt(c -> c.path().estimatedDurationSeconds())).orElseThrow();
        for (int i = 0; i < ranked.size(); i++) {
            Ranked r = ranked.get(i); Candidate c = r.candidate();
            Map<String, Double> components = components(r, c, request.preference());
            results.add(new RouteAlternative(c.path().alternativeId(), i + 1, c.path().provider(), c.path().generated(),
                    c.path().geometry(), c.path().distanceMeters(), c.path().estimatedDurationSeconds(), c.exposure(),
                    c.quality(), round(r.score()), components, reasons(request.preference(), r, c), c.environmentalProvider(), c.coverage(),
                    c.source(), c.sampled(), c.available(), c.unavailable(), c.coveragePercent(), explanation(request.preference(), i + 1,
                    candidates.size(), c, fastest, comparable, r, candidates), c.evidence()));
        }
        boolean generated = paths.stream().anyMatch(RoutePath::generated);
        CalculationResult result = new CalculationResult(UUID.randomUUID(), departure, request.mode(), request.preference(),
                List.copyOf(results), generated, generated ? MOCK_LIMITATION : REAL_ROUTE_LIMITATION);
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

    private final class ExposureSummary {
        private final RoutePath path;
        private final List<RouteSampler.Sample> samples;
        private final String provider;
        private final String source;
        private final boolean fixedCell;
        private final List<String> requiredPollutants;
        private final Map<String, Integer> issues = new java.util.LinkedHashMap<>();
        private final Set<String> missingPollutants = new java.util.TreeSet<>();
        private final Set<String> measurementBases = new java.util.TreeSet<>();
        private double totalWeight;
        private double availableWeight;
        private double weightedBurden;
        private double congestionWeighted;
        private double trafficWeight;
        private int available;
        private double pollutantWeight;
        private int comparisonSamples;
        private int quality = 100;
        private boolean hasQuality;
        private boolean generated;
        private Instant earliest;
        private Instant latest;

        private ExposureSummary(RoutePath path, List<RouteSampler.Sample> samples, boolean fixedCell) {
            this.path = path;
            this.samples = samples;
            this.fixedCell = fixedCell;
            this.provider = environmental == null ? "fixed-cell" : environmental.providerId();
            this.source = fixedCell ? "historical-forecast" : provider;
            List<String> required = environmental == null ? List.of("PM2.5", "PM10", "NO2", "O3")
                    : environmental.requiredComparisonPollutants();
            this.requiredPollutants = required == null || required.isEmpty() ? ObservationModels.POLLUTANTS : required;
            for (int i = 0; i < samples.size(); i++) totalWeight += sampleWeight(path, samples, i);
        }

        private void add(int index, PollutionObservation observation, Integer qualityScore,
                         Double congestion, List<String> responseIssues) {
            if (responseIssues != null) responseIssues.forEach(this::issue);
            if (observation == null) {
                unavailable(List.of("MISSING_MEASUREMENTS"));
                return;
            }
            try {
                var assessment = engine.assess(observation);
                List<String> present = requiredPollutants.stream()
                        .filter(p -> pollutantValue(observation, p) != null).toList();
                requiredPollutants.stream().filter(p -> !present.contains(p)).forEach(missingPollutants::add);
                double weight = sampleWeight(path, samples, index);
                weightedBurden += assessment.burdenScore() * weight;
                pollutantWeight += weight * present.size() / requiredPollutants.size();
                availableWeight += weight;
                available++;
                if (present.containsAll(requiredPollutants) && !assessment.usedAqiFallback()) comparisonSamples++;
                measurementBases.add(observation.provider() + "|" + observation.generated() + "|" + String.join(",", present));
                generated |= observation.generated();
                if (qualityScore != null) {
                    quality = Math.min(quality, qualityScore);
                    hasQuality = true;
                }
                if (congestion != null && Double.isFinite(congestion) && congestion >= 1 && congestion <= 5) {
                    congestionWeighted += congestion * weight;
                    trafficWeight += weight;
                }
                if (observation.observedAt() != null) {
                    earliest = earliest == null || observation.observedAt().isBefore(earliest) ? observation.observedAt() : earliest;
                    latest = latest == null || observation.observedAt().isAfter(latest) ? observation.observedAt() : latest;
                }
            } catch (PollutionEngine.InsufficientPollutionDataException | IllegalArgumentException invalid) {
                unavailable(List.of("INVALID_OR_MISSING_MEASUREMENTS"));
            }
        }

        private void unavailable(List<String> reasons) {
            for (String reason : reasons) issue(reason);
            missingPollutants.addAll(ObservationModels.POLLUTANTS);
        }

        private void issue(String reason) { if (reason != null && !reason.isBlank()) issues.merge(reason, 1, Integer::sum); }

        private Candidate result() {
            double distanceCoverage = totalWeight == 0 ? 0 : round(availableWeight * 100.0 / totalWeight);
            double pollutantCoverage = totalWeight == 0 ? 0 : round(pollutantWeight * 100.0 / totalWeight);
            boolean comparisonEligible = samples.size() > 0 && available == samples.size()
                    && comparisonSamples == samples.size() && measurementBases.size() == 1 && issues.isEmpty();
            boolean complete = available == samples.size() && missingPollutants.isEmpty() && issues.isEmpty();
            String coverage = available == 0 ? "unavailable" : complete ? (fixedCell ? "fixed-cell" : "complete") : "partial";
            EnvironmentalQuality evidence = new EnvironmentalQuality(distanceCoverage, pollutantCoverage,
                    List.copyOf(missingPollutants), issues, fixedCell ? "HISTORICAL_BASELINE" : "MODEL_FORECAST", generated, comparisonEligible,
                    comparisonEligible ? source + "|" + measurementBases.iterator().next() : null, earliest, latest);
            return new Candidate(path, availableWeight == 0 ? null : round(weightedBurden / availableWeight),
                    hasQuality ? quality : null, trafficWeight == 0 ? null : congestionWeighted / trafficWeight,
                    provider, coverage, source, samples.size(), available, samples.size() - available,
                    round(available * 100.0 / samples.size()), evidence);
        }
    }

    private static double sampleWeight(RoutePath path, List<RouteSampler.Sample> samples, int index) {
        if (samples.size() == 2) return Math.max(1, path.distanceMeters() / 2.0);
        if (index == 0) return Math.max(1, samples.get(1).distanceFromStartMeters() / 2.0);
        if (index == samples.size() - 1)
            return Math.max(1, (samples.get(index).distanceFromStartMeters() - samples.get(index - 1).distanceFromStartMeters()) / 2.0);
        return Math.max(1, (samples.get(index + 1).distanceFromStartMeters()
                - samples.get(index - 1).distanceFromStartMeters()) / 2.0);
    }

    private static Double pollutantValue(PollutionObservation observation, String pollutant) {
        return switch (pollutant) {
            case "PM2.5" -> observation.pm25();
            case "PM10" -> observation.pm10();
            case "NO2" -> observation.no2();
            case "SO2" -> observation.so2();
            case "CO" -> observation.co();
            case "O3" -> observation.o3();
            default -> null;
        };
    }

    private Candidate exposure(RoutePath path, Instant departure, List<GeographicCell> cells,
                              Map<String, Optional<PollutionForecast>> forecastCache) {
        if (path.geometry() == null || path.geometry().size() < 2 || !Double.isFinite(path.distanceMeters())
                || path.distanceMeters() <= 0 || path.estimatedDurationSeconds() < 1
                || path.geometry().size() > routingProperties.getMaxGeometryPoints())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Routing provider returned an invalid route");
        if (path.greenAreaCoverage() != null && (!Double.isFinite(path.greenAreaCoverage())
                || path.greenAreaCoverage() < 0 || path.greenAreaCoverage() > 1)
                || path.elevationGainMeters() != null && (!Double.isFinite(path.elevationGainMeters())
                || path.elevationGainMeters() < 0))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Routing provider returned invalid suitability metadata");
        List<Coordinate> points = path.geometry();
        if (points.stream().anyMatch(p -> p == null || !Double.isFinite(p.latitude()) || p.latitude() < -90 || p.latitude() > 90
                || !Double.isFinite(p.longitude()) || p.longitude() < -180 || p.longitude() > 180))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Routing provider returned invalid geometry");
        if (environmental != null && environmental.supportsCoordinateLookup())
            return coordinateExposure(path, departure);
        List<RouteSampler.Sample> samples = RouteSampler.sample(points, environmentalProperties.getRouteSampleIntervalMeters());
        ExposureSummary summary = new ExposureSummary(path, samples, true);
        double sampledDistance = samples.getLast().distanceFromStartMeters();
        for (int i = 0; i < samples.size(); i++) {
            RouteSampler.Sample sample = samples.get(i);
            GeographicCell cell = nearest(sample.coordinate(), cells);
            if (cell == null) { summary.unavailable(List.of("OUTSIDE_FIXED_CELL_COVERAGE")); continue; }
            Instant target = ceilQuarter(departure.plusMillis((long)(path.estimatedDurationSeconds() * 1000.0
                    * sample.distanceFromStartMeters() / Math.max(1, sampledDistance))));
            Optional<PollutionForecast> found = forecastCache.computeIfAbsent(cell.cellId() + "|" + target, ignored -> {
                try { return Optional.ofNullable(forecasts.forecast(cell.cellId(), target)); }
                catch (ResponseStatusException unavailable) { return Optional.empty(); }
            });
            if (found.isEmpty()) { summary.unavailable(List.of("FORECAST_UNAVAILABLE")); continue; }
            PollutionForecast forecast = found.get();
            PollutionObservation observation = new PollutionObservation(cell.cellId(), forecast.targetAt(), forecast.aqi(),
                    forecast.pm25(), forecast.pm10(), forecast.no2(), forecast.so2(), forecast.co(), forecast.o3(),
                    forecast.provider(), forecast.sourceGenerated());
            TrafficObservation traffic = observations.trafficAt(cell.cellId(), target).orElse(null);
            summary.add(i, observation, forecast.qualityScore(), traffic == null ? null : traffic.congestionFactor(), List.of());
        }
        return summary.result();
    }

    private Candidate coordinateExposure(RoutePath path, Instant departure) {
        List<RouteSampler.Sample> samples = RouteSampler.sample(path.geometry(), environmentalProperties.getRouteSampleIntervalMeters());
        double sampledRouteDistance = samples.getLast().distanceFromStartMeters();
        List<EnvironmentalCoordinateQuery> queries = samples.stream().map(sample -> new EnvironmentalCoordinateQuery(
                sample.coordinate(), departure.plusMillis(Math.round(path.estimatedDurationSeconds() * 1000.0
                        * sample.distanceFromStartMeters() / Math.max(sampledRouteDistance, 1.0))))).toList();
        ExposureSummary summary = new ExposureSummary(path, samples, false);
        List<EnvironmentalCoordinateResult> results;
        try { results = environmental.observationsAt(queries); }
        catch (ProviderFailureException failure) {
            for (int i = 0; i < samples.size(); i++) summary.unavailable(List.of(failure.getType().name()));
            return summary.result();
        }
        if (results == null || results.size() != samples.size()) {
            for (int i = 0; i < samples.size(); i++) summary.unavailable(List.of("INVALID_RESPONSE"));
            return summary.result();
        }
        for (int i = 0; i < results.size(); i++) {
            EnvironmentalCoordinateResult result = results.get(i);
            if (result == null || !queries.get(i).equals(result.query())) {
                summary.unavailable(List.of("QUERY_MISMATCH"));
                continue;
            }
            PollutionObservation observation = result.observation();
            if (observation != null && (!environmental.providerId().equals(observation.provider())
                    || observation.observedAt() == null
                    || Duration.between(queries.get(i).observedAt(), observation.observedAt()).abs().compareTo(Duration.ofMinutes(30)) > 0)) {
                summary.unavailable(List.of("INVALID_PROVENANCE_OR_TIME"));
                continue;
            }
            summary.add(i, observation, null, null, result.issues());
        }
        return summary.result();
    }

    private GeographicCell nearest(Coordinate point, List<GeographicCell> cells) {
        return cells.stream().filter(c -> RouteSampler.haversine(point, new Coordinate(c.latitude(), c.longitude()))
                        <= environmentalProperties.getFixedCellCoverageRadiusMeters())
                .min(Comparator.comparingDouble(c -> RouteSampler.haversine(point, new Coordinate(c.latitude(), c.longitude())))).orElse(null);
    }

    private Ranked rank(Candidate c, RoutePreference pref, double maxDuration, boolean comparable) {
        double durationEfficiency = 100.0 * (1 - (double)c.path().estimatedDurationSeconds() / maxDuration);
        double cleanliness = c.exposure() == null ? 0 : clamp(100 - c.exposure());
        double exposure = c.exposure() == null ? 100 : c.exposure();
        boolean pollutionAvailable = comparable && c.evidence().comparisonEligible();
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
            case CLEANEST -> pollutionAvailable ? cleanliness : durationEfficiency;
            case BALANCED -> pollutionAvailable ? 0.5 * durationEfficiency + 0.5 * cleanliness : durationEfficiency;
            case JOGGER -> weightedScore(
                    new double[]{thresholdScore(exposure, suitability.getJoggerMaximumPollutionExposure(), 2),
                            trafficComfort == null ? 0 : trafficComfort, distanceSuitability,
                            greenAreaPreference == null ? 0 : greenAreaPreference},
                    new double[]{suitability.getPollutionWeight(), suitability.getTrafficWeight(),
                            suitability.getDistanceWeight(), 0.05},
                    new boolean[]{pollutionAvailable, trafficComfort != null, true,
                            suitability.isPreferGreenAreas() && greenAreaPreference != null});
            case CYCLIST -> weightedScore(
                     new double[]{thresholdScore(exposure, suitability.getCyclistMaximumPollutionExposure(), 2), trafficComfort == null ? 0 : trafficComfort,
                            cyclingCompatibility == null ? 0 : cyclingCompatibility,
                            elevationSuitability == null ? 0 : elevationSuitability,
                            greenAreaPreference == null ? 0 : greenAreaPreference},
                    new double[]{0.45, 0.30, 0.10, 0.10, 0.05},
                     new boolean[]{pollutionAvailable, trafficComfort != null, cyclingCompatibility != null,
                            elevationSuitability != null, suitability.isPreferGreenAreas() && greenAreaPreference != null});
            default -> throw new IllegalArgumentException("Unsupported route preference");
        };
        return new Ranked(c, durationEfficiency, score, trafficComfort, distanceSuitability,
                cyclingCompatibility, elevationSuitability, greenAreaPreference, pollutionAvailable);
    }

    private SelectionReason explanation(RoutePreference preference, int rank, int availableRoutes, Candidate route,
                                         Candidate fastest, boolean comparable, Ranked ranked,
                                         List<Candidate> candidates) {
        Candidate exposureComparator = comparable ? candidates.stream().filter(c -> c != route
                        && c.evidence().comparisonEligible() && c.exposure() != null)
                .min(Comparator.comparingDouble(Candidate::exposure)).orElse(null) : null;
        List<String> factors = switch (preference) {
            case FASTEST -> List.of("Ordered by estimated travel duration among returned routing candidates.");
            case CLEANEST -> !comparable || !route.evidence().comparisonEligible()
                    ? List.of("Environmental candidates were not comparable, so CLEANEST used duration as a safe fallback.")
                    : List.of("Exposure is route-distance-weighted at estimated passage times.",
                    "Environmental distance coverage: " + route.evidence().distanceCoveragePercent() + "%; pollutant coverage: "
                            + route.evidence().pollutantCoveragePercent() + "%.",
                    "Environmental provenance: " + route.source() + ".");
            case BALANCED -> comparable
                    ? List.of("Uses an equal-weight comparison: 50% duration efficiency and 50% modeled pollution cleanliness for every candidate.")
                    : List.of("Environmental candidates were not comparable, so BALANCED used duration as a safe fallback.");
            case JOGGER, CYCLIST -> reasons(preference, ranked, route);
        };
        String headline;
        String summary;
        if (preference == RoutePreference.CLEANEST && availableRoutes == 1) {
            headline = "No route comparison available";
            summary = "Only one routing candidate was available, so a cleanest-vs-alternatives comparison could not be established.";
        } else if ((preference == RoutePreference.CLEANEST || preference == RoutePreference.BALANCED) && !comparable) {
            headline = preference == RoutePreference.CLEANEST ? "Cleanest route could not be established" : "Duration-only fallback";
            summary = preference == RoutePreference.CLEANEST
                    ? "Environmental data was incomplete or incomparable, so routes were ordered by duration without claiming a cleanest route."
                    : "Environmental data was incomplete or incomparable, so the balanced comparison used duration without a pollution component.";
        } else if (preference == RoutePreference.CLEANEST && candidates.stream().map(Candidate::exposure).distinct().count() == 1) {
            headline = "Environmental data did not distinguish the available routes";
            summary = "Modeled exposures are tied; duration breaks ties. A uniquely cleaner route could not be established.";
        } else {
            headline = switch (preference) {
                case FASTEST -> rank == 1 ? "Fastest available route" : "Travel-time comparison";
                case CLEANEST -> rank == 1 ? "Lowest modeled pollution exposure" : "Modeled exposure comparison";
                case BALANCED -> rank == 1 ? "Best balanced comparison" : "Balanced comparison";
                case JOGGER -> rank == 1 ? "Best jogger suitability score" : "Jogger suitability comparison";
                case CYCLIST -> rank == 1 ? "Best cyclist suitability score" : "Cyclist suitability comparison";
            };
            summary = switch (preference) {
                case CLEANEST -> rank == 1
                        ? "Lowest modeled pollution exposure among " + availableRoutes + " available routing candidate" + (availableRoutes == 1 ? "." : "s.")
                        : "Ranked " + rank + " of " + availableRoutes + " candidates by modeled exposure.";
                case FASTEST -> rank == 1 ? "Shortest estimated travel time among returned candidates."
                        : "Ranked " + rank + " of " + availableRoutes + " candidates by estimated travel time.";
                case BALANCED -> rank == 1 ? "Selected by the balanced duration and pollution comparison."
                        : "Ranked " + rank + " of " + availableRoutes + " candidates by the balanced comparison.";
                default -> factors.getFirst();
            };
        }
        Candidate exposureReference = exposureComparator;
        Double exposureDifference = comparable && route.exposure() != null && exposureReference != null && exposureReference.exposure() != null
                && exposureReference.exposure() > 0 ? round(100 * (route.exposure() - exposureReference.exposure()) / exposureReference.exposure()) : null;
        Integer extraDuration = fastest == null ? null : route.path().estimatedDurationSeconds() - fastest.path().estimatedDurationSeconds();
        Double extraDistance = fastest == null ? null : round(route.path().distanceMeters() - fastest.path().distanceMeters());
        return new SelectionReason(preference.name(), headline, summary,
                new RouteComparison(availableRoutes, rank, exposureDifference, extraDuration, extraDistance,
                        exposureReference == null ? null : exposureReference.path().alternativeId(), fastest == null ? null : fastest.path().alternativeId()), factors);
    }

    private List<String> reasons(RoutePreference preference, Ranked ranked, Candidate candidate) {
        return switch (preference) {
            case FASTEST -> List.of("Ranked by estimated travel time.", "Pollution exposure is reported but does not change FASTEST ordering.");
            case CLEANEST -> !ranked.pollutionAvailable() ? List.of("Environmental candidates were incomplete or incomparable; CLEANEST used estimated duration without claiming lower pollution.") : List.of("Ranked by route-distance-weighted pollution exposures evaluated at estimated passage times.", "Duration breaks exposure ties; alternative ID breaks remaining ties.");
            case BALANCED -> ranked.pollutionAvailable() ? List.of("Balances normalized travel-time efficiency and pollution cleanliness equally.") : List.of("Environmental candidates were incomplete or incomparable; BALANCED used estimated duration only.");
            case JOGGER -> {
                List<String> reasons = new ArrayList<>(List.of("Ranks lower-pollution, lower-traffic routes and favors distances around the configured jogger range."));
                if (!ranked.pollutionAvailable()) reasons.add("Pollution inputs were incomparable and excluded from all candidates' suitability scores.");
                if (candidate.congestionFactor() == null) reasons.add("Traffic data is unavailable and was excluded from this score.");
                if (!suitability.isPreferGreenAreas()) reasons.add("Green-area preference is disabled and its metadata was not used.");
                else if (candidate.path().greenAreaCoverage() == null) reasons.add("Green-area metadata is unavailable and was not used.");
                else reasons.add("Green-area coverage metadata was used to favor routes with more green-area coverage.");
                yield List.copyOf(reasons);
            }
            case CYCLIST -> {
                List<String> reasons = new ArrayList<>(List.of("Ranks lower-pollution and lower-traffic routes."));
                if (!ranked.pollutionAvailable()) reasons.add("Pollution inputs were incomparable and excluded from all candidates' suitability scores.");
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
        if (candidate.exposure() != null && ranked.pollutionAvailable()) {
            values.put("pollutionCleanliness", clamp(100 - candidate.exposure()));
            values.put("expectedPollutionExposure", candidate.exposure());
        }
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
    private static double round(double n) { return Math.round(n * 100.0) / 100.0; }
    private static String coordinateName(Coordinate coordinate) {
        return String.format(Locale.ROOT, "%.5f, %.5f", coordinate.latitude(), coordinate.longitude());
    }
    private record Candidate(RoutePath path, Double exposure, Integer quality, Double congestionFactor,
                              String environmentalProvider, String coverage, String source, int sampled,
                              int available, int unavailable, Double coveragePercent, EnvironmentalQuality evidence) {}
    private record Ranked(Candidate candidate, double durationEfficiency, double score, Double trafficComfort,
                          Double distanceSuitability, Double cyclingCompatibility, Double elevationSuitability,
                          Double greenAreaPreference, boolean pollutionAvailable) {}
}
