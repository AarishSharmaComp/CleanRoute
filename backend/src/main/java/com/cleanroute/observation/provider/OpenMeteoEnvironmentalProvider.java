package com.cleanroute.observation.provider;

import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.LocalDateTime;

@Component
@ConditionalOnProperty(name = "app.environmental.provider", havingValue = "open-meteo")
public class OpenMeteoEnvironmentalProvider implements EnvironmentalDataProvider {
    private static final int MAX_COORDINATES_PER_REQUEST = 100;
    private static final String UNIT = "μg/m³";
    private final EnvironmentalProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;

    public OpenMeteoEnvironmentalProvider(EnvironmentalProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout(properties.getConnectTimeoutMs()))).build();
    }

    @Override public String providerId() { return "open-meteo-air-quality"; }
    @Override public boolean supportsHistoricalIngestion() { return false; }
    @Override public boolean supportsCoordinateLookup() { return true; }

    @Override
    public List<EnvironmentalCoordinateResult> observationsAt(List<EnvironmentalCoordinateQuery> queries) {
        if (queries == null || queries.isEmpty()) throw ProviderFailureException.temporary();
        List<EnvironmentalCoordinateResult> results = new ArrayList<>(Collections.nCopies(queries.size(), null));
        Instant now = Instant.now();
        for (int start = 0; start < queries.size(); start += MAX_COORDINATES_PER_REQUEST) {
            List<EnvironmentalCoordinateQuery> batch = new ArrayList<>();
            List<Integer> positions = new ArrayList<>();
            for (int i = start; i < Math.min(queries.size(), start + MAX_COORDINATES_PER_REQUEST); i++) {
                EnvironmentalCoordinateQuery query = queries.get(i);
                if (query == null || query.coordinate() == null || query.observedAt() == null
                        || !valid(query.coordinate().latitude(), query.coordinate().longitude())) {
                    results.set(i, unavailable(query, "INVALID_QUERY"));
                } else if (query.observedAt().isBefore(now.minus(Duration.ofHours(1)))
                        || query.observedAt().isAfter(now.plus(Duration.ofDays(7)))) {
                    results.set(i, unavailable(query, "UNSUPPORTED_TIME"));
                } else {
                    batch.add(query);
                    positions.add(i);
                }
            }
            if (batch.isEmpty()) continue;
            try {
                List<EnvironmentalCoordinateResult> received = observationsAtBatch(batch);
                for (int i = 0; i < received.size(); i++) results.set(positions.get(i), received.get(i));
            } catch (ProviderFailureException failure) {
                // Keep successful batches and the original query order. Never substitute demo data.
                for (int position : positions)
                    results.set(position, unavailable(queries.get(position), failure.getType().name()));
            }
        }
        return List.copyOf(results);
    }

    private List<EnvironmentalCoordinateResult> observationsAtBatch(List<EnvironmentalCoordinateQuery> queries) {
        Instant start = queries.stream().map(EnvironmentalCoordinateQuery::observedAt).min(Instant::compareTo).orElseThrow();
        Instant end = queries.stream().map(EnvironmentalCoordinateQuery::observedAt).max(Instant::compareTo).orElseThrow();
        try {
            String latitudes = queries.stream().map(q -> Double.toString(q.coordinate().latitude())).collect(Collectors.joining(","));
            String longitudes = queries.stream().map(q -> Double.toString(q.coordinate().longitude())).collect(Collectors.joining(","));
            String query = "?latitude=" + latitudes + "&longitude=" + longitudes
                    + "&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone"
                    + "&start_hour=" + hour(start.truncatedTo(ChronoUnit.HOURS))
                    + "&end_hour=" + hour(nearestHour(end)) + "&timezone=UTC";
            HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(properties.getOpenMeteoUrl(), query)))
                    .header("Accept", "application/json").timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs()))).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofMinutes(1));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderFailureException.temporary();
            JsonNode root = mapper.readTree(response.body());
            if (root == null || (!root.isArray() && !(queries.size() == 1 && root.isObject()))
                    || root.isArray() && root.size() != queries.size()) throw ProviderFailureException.temporary();
            List<EnvironmentalCoordinateResult> results = new ArrayList<>();
            for (int i = 0; i < queries.size(); i++) {
                EnvironmentalCoordinateQuery requested = queries.get(i);
                try {
                    results.add(normalizeHourly(root.isArray() ? root.get(i) : root, requested));
                } catch (RuntimeException malformed) {
                    results.add(unavailable(requested, "INVALID_RESPONSE"));
                }
            }
            return List.copyOf(results);
        } catch (ProviderFailureException failure) { throw failure;
        } catch (HttpTimeoutException timeout) { throw ProviderFailureException.timeout();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw ProviderFailureException.temporary();
        } catch (Exception failure) { throw ProviderFailureException.temporary(); }
    }

    @Override
    public List<PollutionObservation> observations(GeographicCell cell, Instant ignoredTimestamp) {
        if (cell == null || !valid(cell.latitude(), cell.longitude())) throw ProviderFailureException.temporary();
        try {
            String query = "?latitude=" + cell.latitude() + "&longitude=" + cell.longitude()
                    + "&current=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone&timezone=UTC";
            HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(properties.getOpenMeteoUrl(), query)))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs())))
                    .GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofMinutes(1));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderFailureException.temporary();
            return List.of(normalize(response.body(), cell));
        } catch (ProviderFailureException failure) {
            throw failure;
        } catch (HttpTimeoutException timeout) {
            throw ProviderFailureException.timeout();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw ProviderFailureException.temporary();
        } catch (Exception failure) {
            throw ProviderFailureException.temporary();
        }
    }

    public PollutionObservation normalize(String body, GeographicCell cell) {
        try {
            JsonNode root = mapper.readTree(body);
            JsonNode current = root == null ? null : root.get("current");
            if (current == null || !current.isObject()) throw ProviderFailureException.temporary();
            requireUtcResponse(root);
            Instant observedAt = providerInstant(current.path("time").asText());
            PollutionObservation observation = measurements(cell.cellId(), observedAt, current,
                    root.path("current_units"), -1, 0, new ArrayList<>());
            if (observation == null) throw ProviderFailureException.temporary();
            return observation;
        } catch (ProviderFailureException failure) {
            throw failure;
        } catch (Exception malformed) {
            throw ProviderFailureException.temporary();
        }
    }

    private EnvironmentalCoordinateResult normalizeHourly(JsonNode root, EnvironmentalCoordinateQuery query) {
        requireUtcResponse(root);
        JsonNode units = root.path("hourly_units"), hourly = root.path("hourly");
        JsonNode times = hourly.path("time");
        if (!times.isArray() || times.isEmpty()) throw ProviderFailureException.temporary();
        Instant expected = nearestHour(query.observedAt());
        Instant previous = null;
        int matched = -1;
        for (int i = 0; i < times.size(); i++) {
            Instant time = providerInstant(times.get(i).asText());
            if (!time.equals(time.truncatedTo(ChronoUnit.HOURS)) || previous != null && !time.isAfter(previous))
                return unavailable(query, "INVALID_TIMESTAMP");
            if (time.equals(expected)) matched = i;
            previous = time;
        }
        // Require the actual nearest hourly timestep, at most 30 minutes away. No extrapolation.
        if (matched < 0) return unavailable(query, "NO_MATCHING_HOUR");
        List<String> issues = new ArrayList<>();
        PollutionObservation observation = measurements(coordinateId(query.coordinate()), expected,
                hourly, units, matched, times.size(), issues);
        if (observation == null) issues.add("MISSING_MEASUREMENTS");
        return new EnvironmentalCoordinateResult(query, observation, issues);
    }

    private PollutionObservation measurements(String cellId, Instant at, JsonNode values, JsonNode units,
                                               int index, int timeCount, List<String> issues) {
        Double pm10 = value(values, units, "pm10", index, timeCount, issues);
        Double pm25 = value(values, units, "pm2_5", index, timeCount, issues);
        Double co = value(values, units, "carbon_monoxide", index, timeCount, issues);
        Double no2 = value(values, units, "nitrogen_dioxide", index, timeCount, issues);
        Double so2 = value(values, units, "sulphur_dioxide", index, timeCount, issues);
        Double o3 = value(values, units, "ozone", index, timeCount, issues);
        if (pm10 == null && pm25 == null && co == null && no2 == null && so2 == null && o3 == null) return null;
        return new PollutionObservation(cellId, at, null, pm25, pm10, no2, so2,
                co == null ? null : co / 1000.0, o3, providerId(), false);
    }

    private static Double value(JsonNode values, JsonNode units, String field, int index,
                                int timeCount, List<String> issues) {
        JsonNode measurement = values.path(field);
        if (measurement.isMissingNode() || measurement.isNull()) return null;
        if (index >= 0) {
            if (!measurement.isArray() || measurement.size() != timeCount) {
                issues.add("INVALID_POLLUTANT:" + field);
                return null;
            }
            measurement = measurement.get(index);
        }
        if (measurement.isNull()) return null;
        if (!measurement.isNumber() || !Double.isFinite(measurement.doubleValue()) || measurement.doubleValue() < 0
                || !UNIT.equals(units.path(field).asText())) {
            issues.add("INVALID_POLLUTANT:" + field);
            return null;
        }
        return measurement.doubleValue();
    }

    private static void requireUtcResponse(JsonNode root) {
        if (root == null || !root.isObject() || root.path("error").asBoolean(false))
            throw ProviderFailureException.temporary();
        JsonNode offset = root.get("utc_offset_seconds");
        if (offset != null && (!offset.isIntegralNumber() || offset.longValue() != 0))
            throw ProviderFailureException.temporary();
    }

    /** timezone=UTC is always requested. Offset-less provider timestamps therefore mean UTC. */
    private static Instant providerInstant(String value) {
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeParseException noOffset) { return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC); }
    }

    private static Instant nearestHour(Instant time) {
        Instant floor = time.truncatedTo(ChronoUnit.HOURS);
        return Duration.between(floor, time).compareTo(Duration.ofMinutes(30)) <= 0 ? floor : floor.plus(1, ChronoUnit.HOURS);
    }

    private static EnvironmentalCoordinateResult unavailable(EnvironmentalCoordinateQuery query, String reason) {
        return new EnvironmentalCoordinateResult(query, null, List.of(reason));
    }

    private static String hour(Instant instant) { return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC).format(instant); }
    private static String appendQuery(String base, String query) { return base + (base.contains("?") ? "&" : "?") + query.substring(1); }
    private static String coordinateId(com.cleanroute.observation.domain.RoutingModels.Coordinate c) { return "coordinate:" + c.latitude() + ":" + c.longitude(); }

    private static boolean valid(double latitude, double longitude) {
        return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }
    private static long timeout(int value) { return Math.max(100, value); }
}
