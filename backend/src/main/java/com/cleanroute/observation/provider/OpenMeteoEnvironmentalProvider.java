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
import java.util.List;
import java.util.ArrayList;
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
        List<EnvironmentalCoordinateResult> results = new ArrayList<>(queries.size());
        for (int start = 0; start < queries.size(); start += MAX_COORDINATES_PER_REQUEST) {
            List<EnvironmentalCoordinateQuery> batch = queries.subList(start,
                    Math.min(queries.size(), start + MAX_COORDINATES_PER_REQUEST));
            results.addAll(observationsAtBatch(batch));
        }
        return List.copyOf(results);
    }

    private List<EnvironmentalCoordinateResult> observationsAtBatch(List<EnvironmentalCoordinateQuery> queries) {
        Instant start = queries.stream().map(EnvironmentalCoordinateQuery::observedAt).min(Instant::compareTo).orElseThrow();
        Instant end = queries.stream().map(EnvironmentalCoordinateQuery::observedAt).max(Instant::compareTo).orElseThrow();
        if (start.isBefore(Instant.now().minus(Duration.ofHours(1))) || end.isAfter(Instant.now().plus(Duration.ofDays(7))))
            throw ProviderFailureException.temporary();
        try {
            String latitudes = queries.stream().map(q -> Double.toString(q.coordinate().latitude())).reduce((a, b) -> a + "," + b).orElseThrow();
            String longitudes = queries.stream().map(q -> Double.toString(q.coordinate().longitude())).reduce((a, b) -> a + "," + b).orElseThrow();
            String query = "?latitude=" + latitudes + "&longitude=" + longitudes
                    + "&hourly=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone"
                    + "&start_hour=" + hour(start) + "&end_hour=" + hour(end.plus(Duration.ofHours(1))) + "&timezone=UTC";
            HttpRequest request = HttpRequest.newBuilder(URI.create(appendQuery(properties.getOpenMeteoUrl(), query)))
                    .header("Accept", "application/json").timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs()))).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofMinutes(1));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderFailureException.temporary();
            JsonNode root = mapper.readTree(response.body());
            if (root == null || !root.isArray() || root.size() != queries.size()) throw ProviderFailureException.temporary();
            List<EnvironmentalCoordinateResult> results = new ArrayList<>();
            for (int i = 0; i < queries.size(); i++) results.add(new EnvironmentalCoordinateResult(queries.get(i), normalizeHourly(root.get(i), queries.get(i))));
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
            requireUnit(root.path("current_units"), "pm10");
            requireUnit(root.path("current_units"), "pm2_5");
            requireUnit(root.path("current_units"), "carbon_monoxide");
            requireUnit(root.path("current_units"), "nitrogen_dioxide");
            requireUnit(root.path("current_units"), "sulphur_dioxide");
            requireUnit(root.path("current_units"), "ozone");
            Instant observedAt = Instant.parse(current.path("time").asText());
            Double pm10 = value(current, "pm10");
            Double pm25 = value(current, "pm2_5");
            Double co = value(current, "carbon_monoxide");
            Double no2 = value(current, "nitrogen_dioxide");
            Double so2 = value(current, "sulphur_dioxide");
            Double o3 = value(current, "ozone");
            if (pm10 == null && pm25 == null && co == null && no2 == null && so2 == null && o3 == null)
                throw ProviderFailureException.temporary();
            return new PollutionObservation(cell.cellId(), observedAt, null, pm25, pm10, no2, so2,
                    co == null ? null : co / 1000.0, o3, providerId(), false);
        } catch (ProviderFailureException failure) {
            throw failure;
        } catch (Exception malformed) {
            throw ProviderFailureException.temporary();
        }
    }

    private PollutionObservation normalizeHourly(JsonNode root, EnvironmentalCoordinateQuery query) {
        try {
            JsonNode units = root.path("hourly_units"), hourly = root.path("hourly");
            if (!hourly.isObject()) throw ProviderFailureException.temporary();
            for (String field : List.of("pm10", "pm2_5", "carbon_monoxide", "nitrogen_dioxide", "sulphur_dioxide", "ozone"))
                requireUnit(units, field);
            JsonNode times = hourly.path("time");
            if (!times.isArray() || times.isEmpty()) throw ProviderFailureException.temporary();
            int nearest = 0;
            long distance = Long.MAX_VALUE;
            for (int i = 0; i < times.size(); i++) {
                long candidate = Math.abs(Duration.between(query.observedAt(), hourlyInstant(times.get(i).asText())).toSeconds());
                if (candidate < distance) { distance = candidate; nearest = i; }
            }
            Instant observedAt = hourlyInstant(times.get(nearest).asText());
            Double pm10 = hourlyValue(hourly, "pm10", nearest), pm25 = hourlyValue(hourly, "pm2_5", nearest);
            Double co = hourlyValue(hourly, "carbon_monoxide", nearest), no2 = hourlyValue(hourly, "nitrogen_dioxide", nearest);
            Double so2 = hourlyValue(hourly, "sulphur_dioxide", nearest), o3 = hourlyValue(hourly, "ozone", nearest);
            if (pm10 == null && pm25 == null && co == null && no2 == null && so2 == null && o3 == null) return null;
            return new PollutionObservation(coordinateId(query.coordinate()), observedAt, null, pm25, pm10, no2, so2,
                    co == null ? null : co / 1000.0, o3, providerId(), false);
        } catch (ProviderFailureException failure) { throw failure;
        } catch (Exception malformed) { throw ProviderFailureException.temporary(); }
    }

    private static Double hourlyValue(JsonNode hourly, String field, int index) {
        JsonNode values = hourly.path(field);
        if (!values.isArray() || index >= values.size() || values.get(index).isNull()) return null;
        double value = values.get(index).asDouble(Double.NaN);
        if (!Double.isFinite(value) || value < 0) throw ProviderFailureException.temporary();
        return value;
    }

    private static Instant hourlyInstant(String value) {
        return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
    }

    private static String hour(Instant instant) { return DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(ZoneOffset.UTC).format(instant); }
    private static String appendQuery(String base, String query) { return base + (base.contains("?") ? "&" : "?") + query.substring(1); }
    private static String coordinateId(com.cleanroute.observation.domain.RoutingModels.Coordinate c) { return "coordinate:" + c.latitude() + ":" + c.longitude(); }

    private static void requireUnit(JsonNode units, String field) {
        String unit = units.path(field).asText("");
        if (!UNIT.equals(unit)) throw ProviderFailureException.temporary();
    }
    private static Double value(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) return null;
        double number = value.asDouble(Double.NaN);
        if (!Double.isFinite(number) || number < 0) throw ProviderFailureException.temporary();
        return number;
    }
    private static boolean valid(double latitude, double longitude) {
        return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }
    private static long timeout(int value) { return Math.max(100, value); }
}
