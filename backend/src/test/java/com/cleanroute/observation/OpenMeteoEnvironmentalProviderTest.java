package com.cleanroute.observation;

import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.provider.OpenMeteoEnvironmentalProvider;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.provider.EnvironmentalCoordinateQuery;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMeteoEnvironmentalProviderTest {
    private HttpServer server;

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void normalizesDocumentedCurrentMeasurementsAndLeavesAqiAbsent() throws Exception {
        var provider = provider("""
                {"utc_offset_seconds":0,"timezone":"GMT","current_units":{"time":"iso8601","pm10":"μg/m³","pm2_5":"μg/m³","carbon_monoxide":"μg/m³","nitrogen_dioxide":"μg/m³","sulphur_dioxide":"μg/m³","ozone":"μg/m³"},
                 "current":{"time":"2026-09-29T08:00","pm10":102.4,"pm2_5":30.0,"carbon_monoxide":406.0,"nitrogen_dioxide":5.3,"sulphur_dioxide":18.2,"ozone":168.0}}
                """);

        var observation = provider.observations(new GeographicCell("cell-a", 28.6, 77.2), Instant.now()).getFirst();

        assertThat(observation.provider()).isEqualTo("open-meteo-air-quality");
        assertThat(observation.generated()).isFalse();
        assertThat(observation.aqi()).isNull();
        assertThat(observation.observedAt()).isEqualTo(Instant.parse("2026-09-29T08:00:00Z"));
        assertThat(observation.pm10()).isEqualTo(102.4);
        assertThat(observation.pm25()).isEqualTo(30.0);
        assertThat(observation.co()).isEqualTo(0.406);
        assertThat(observation.no2()).isEqualTo(5.3);
        assertThat(observation.so2()).isEqualTo(18.2);
        assertThat(observation.o3()).isEqualTo(168.0);
    }

    @Test void preservesMissingMeasurementsAsNull() {
        var provider = new OpenMeteoEnvironmentalProvider(properties(""), new ObjectMapper());
        var observation = provider.normalize("""
                {"current_units":{"pm10":"μg/m³","pm2_5":"μg/m³","carbon_monoxide":"μg/m³","nitrogen_dioxide":"μg/m³","sulphur_dioxide":"μg/m³","ozone":"μg/m³"},
                 "current":{"time":"2026-09-29T08:00:00Z","pm10":null,"pm2_5":12.5,"carbon_monoxide":null,"nitrogen_dioxide":null,"sulphur_dioxide":null,"ozone":null}}
                """, new GeographicCell("cell-a", 28.6, 77.2));

        assertThat(observation.pm25()).isEqualTo(12.5);
        assertThat(observation.pm10()).isNull();
        assertThat(observation.co()).isNull();
        assertThat(observation.aqi()).isNull();
    }

    @Test void coordinateResponsePreservesMissingPollutantsAndReturnsUnavailableForEmptySample() throws Exception {
        var query = new EnvironmentalCoordinateQuery(new Coordinate(28.6, 77.2), passage());
        String response = "[{\"hourly_units\":{\"time\":\"iso8601\",\"pm10\":\"μg/m³\",\"pm2_5\":\"μg/m³\",\"carbon_monoxide\":\"μg/m³\",\"nitrogen_dioxide\":\"μg/m³\",\"sulphur_dioxide\":\"μg/m³\",\"ozone\":\"μg/m³\"},"
                + "\"hourly\":{\"time\":[\"" + hour(query.observedAt()) + "\"],\"pm10\":[null],\"pm2_5\":[12.5],\"carbon_monoxide\":[null],\"nitrogen_dioxide\":[null],\"sulphur_dioxide\":[null],\"ozone\":[null]}}]";
        var provider = provider(response);
        var result = provider.observationsAt(List.of(query)).getFirst().observation();
        assertThat(result.pm25()).isEqualTo(12.5);
        assertThat(result.pm10()).isNull();
        assertThat(result.co()).isNull();
        assertThat(result.aqi()).isNull();
    }

    @Test void malformedResponseAndHttpFailureDoNotBecomeObservations() throws Exception {
        var provider = provider("not-json");
        assertThatThrownBy(() -> provider.observations(cell(), Instant.now()))
                .isInstanceOf(ProviderFailureException.class);

        server.stop(0);
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(503, 0);
            exchange.close();
        });
        server.start();
        var failed = new OpenMeteoEnvironmentalProvider(properties("http://localhost:" + server.getAddress().getPort()), new ObjectMapper());
        assertThatThrownBy(() -> failed.observations(cell(), Instant.now()))
                .isInstanceOf(ProviderFailureException.class);
    }

    @Test void excludesUnexpectedUnitsWithoutLosingOtherValidPollutants() {
        var provider = new OpenMeteoEnvironmentalProvider(properties(""), new ObjectMapper());
        var observation = provider.normalize("""
                {"current_units":{"pm10":"mg/m³","pm2_5":"μg/m³","carbon_monoxide":"μg/m³","nitrogen_dioxide":"μg/m³","sulphur_dioxide":"μg/m³","ozone":"μg/m³"},
                 "current":{"time":"2026-09-29T08:00:00Z","pm10":1,"pm2_5":1,"carbon_monoxide":1,"nitrogen_dioxide":1,"sulphur_dioxide":1,"ozone":1}}
                """, cell());
        assertThat(observation.pm10()).isNull();
        assertThat(observation.pm25()).isEqualTo(1.0);
    }

    @Test void batchesCoordinateQueriesAndSelectsHourlyPassageValue() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        Instant passage = passage();
        String firstHour = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                .withZone(java.time.ZoneOffset.UTC).format(passage);
        String secondHour = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
                .withZone(java.time.ZoneOffset.UTC).format(passage.plusSeconds(3600));
        server.createContext("/", exchange -> {
            String request = exchange.getRequestURI().toString();
            if (!request.contains("latitude=28.6,28.7") || !request.contains("start_hour=")) throw new AssertionError(request);
            String body = "[{\"hourly_units\":{\"time\":\"iso8601\",\"pm10\":\"μg/m³\",\"pm2_5\":\"μg/m³\",\"carbon_monoxide\":\"μg/m³\",\"nitrogen_dioxide\":\"μg/m³\",\"sulphur_dioxide\":\"μg/m³\",\"ozone\":\"μg/m³\"},"
                    + "\"hourly\":{\"time\":[\"" + firstHour + "\",\"" + secondHour + "\"],\"pm10\":[10,20],\"pm2_5\":[5,10],\"carbon_monoxide\":[100,200],\"nitrogen_dioxide\":[1,2],\"sulphur_dioxide\":[1,2],\"ozone\":[30,40]}},"
                    + "{\"hourly_units\":{\"time\":\"iso8601\",\"pm10\":\"μg/m³\",\"pm2_5\":\"μg/m³\",\"carbon_monoxide\":\"μg/m³\",\"nitrogen_dioxide\":\"μg/m³\",\"sulphur_dioxide\":\"μg/m³\",\"ozone\":\"μg/m³\"},"
                    + "\"hourly\":{\"time\":[\"" + firstHour + "\",\"" + secondHour + "\"],\"pm10\":[30,40],\"pm2_5\":[15,20],\"carbon_monoxide\":[300,400],\"nitrogen_dioxide\":[3,4],\"sulphur_dioxide\":[3,4],\"ozone\":[50,60]}}]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var provider = new OpenMeteoEnvironmentalProvider(properties("http://localhost:" + server.getAddress().getPort()), new ObjectMapper());
        var queries = List.of(new EnvironmentalCoordinateQuery(new Coordinate(28.6, 77.2), passage.plusSeconds(20 * 60)),
                new EnvironmentalCoordinateQuery(new Coordinate(28.7, 77.3), passage.plusSeconds(40 * 60)));

        var results = provider.observationsAt(queries);

        assertThat(results).hasSize(2);
        assertThat(results.getFirst().observation().pm25()).isEqualTo(5.0);
        assertThat(results.getFirst().observation().co()).isEqualTo(0.1);
        assertThat(results.getFirst().observation().observedAt()).isEqualTo(java.time.LocalDateTime.parse(firstHour).toInstant(java.time.ZoneOffset.UTC));
        assertThat(results.get(1).observation().pm25()).isEqualTo(20.0);
    }

    @ParameterizedTest @ValueSource(ints = {100, 101, 201})
    void batchesLargeRouteSamplesWithoutDroppingOrder(int size) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        var requests = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String uri = exchange.getRequestURI().toString();
            String item = hourlyFixture(passage()).toString();
            int actualCount = uri.contains("latitude=") ? uri.split("latitude=")[1].split("&")[0].split(",").length : 0;
            String body = actualCount == 1 ? item : "[" + java.util.stream.IntStream.range(0, actualCount).mapToObj(ignored -> item).collect(java.util.stream.Collectors.joining(",")) + "]";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var provider = new OpenMeteoEnvironmentalProvider(properties("http://localhost:" + server.getAddress().getPort()), new ObjectMapper());
        List<EnvironmentalCoordinateQuery> queries = new java.util.ArrayList<>();
        Instant passage = passage();
        for (int i = 0; i < size; i++) queries.add(new EnvironmentalCoordinateQuery(new Coordinate(28.6 + i * 0.001, 77.2), passage));

        var results = provider.observationsAt(queries);

        assertThat(requests).hasValue((size + 99) / 100);
        assertThat(results).hasSize(size);
        for (int i = 0; i < size; i++) {
            assertThat(results.get(i).query()).isEqualTo(queries.get(i));
            assertThat(results.get(i).observation().pm25()).isEqualTo(5.0);
            assertThat(results.get(i).issues()).isEmpty();
        }
    }

    @ParameterizedTest @ValueSource(strings = {"object", "array"})
    void acceptsSingleCoordinateObjectsAndArrays(String shape) throws Exception {
        String item = hourlyFixture(passage()).toString();
        var result = provider(shape.equals("object") ? item : "[" + item + "]")
                .observationsAt(List.of(query(passage()))).getFirst();
        assertThat(result.observation().observedAt()).isEqualTo(passage());
        assertThat(result.observation().co()).isEqualTo(0.1);
        assertThat(result.observation().aqi()).isNull();
    }

    @Test void halfHourTieUsesEarlierHourAndMissingNearestHourIsNotExtrapolated() throws Exception {
        var provider = provider(hourlyFixture(passage()).toString());
        var results = provider.observationsAt(List.of(query(passage().plusSeconds(1800))));
        assertThat(results.getFirst().observation().observedAt()).isEqualTo(passage());
        var missing = provider.observationsAt(List.of(query(passage().plusSeconds(1801)))).getFirst();
        assertThat(missing.observation()).isNull();
        assertThat(missing.issues()).contains("NO_MATCHING_HOUR");
    }

    @ParameterizedTest @ValueSource(strings = {"malformed", "distant", "unaligned", "duplicate", "nonUtc"})
    void rejectsInvalidOrDistantHourlyObservations(String failure) throws Exception {
        ObjectNode fixture = hourlyFixture(passage());
        var times = ((ObjectNode) fixture.path("hourly")).putArray("time");
        switch (failure) {
            case "malformed" -> times.add("not-a-time");
            case "distant" -> times.add(hour(passage().minus(2, ChronoUnit.HOURS)));
            case "unaligned" -> times.add(hour(passage().plusSeconds(60)));
            case "duplicate" -> { times.add(hour(passage())); times.add(hour(passage())); }
            default -> { times.add(hour(passage())); fixture.put("utc_offset_seconds", 19800); }
        }
        var result = provider(fixture.toString()).observationsAt(List.of(query(passage()))).getFirst();
        assertThat(result.observation()).isNull();
        assertThat(result.issues()).isNotEmpty();
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "null", "negative", "string", "boolean", "shortArray", "wrongUnit"})
    void invalidPollutantDoesNotBecomeZeroOrDiscardValidFields(String failure) throws Exception {
        ObjectNode fixture = hourlyFixture(passage());
        ObjectNode values = (ObjectNode) fixture.path("hourly");
        switch (failure) {
            case "missing" -> values.remove("pm10");
            case "null" -> values.putArray("pm10").addNull();
            case "negative" -> values.putArray("pm10").add(-1);
            case "string" -> values.putArray("pm10").add("12.5");
            case "boolean" -> values.putArray("pm10").add(false);
            case "shortArray" -> values.putArray("pm10");
            default -> ((ObjectNode) fixture.path("hourly_units")).put("pm10", "mg/m³");
        }
        var result = provider(fixture.toString()).observationsAt(List.of(query(passage()))).getFirst();
        assertThat(result.observation().pm10()).isNull();
        assertThat(result.observation().pm25()).isEqualTo(5.0);
        if (!List.of("missing", "null").contains(failure)) assertThat(result.issues()).contains("INVALID_POLLUTANT:pm10");
    }

    @Test void allMissingPollutantsRemainUnavailableButRealZeroIsValid() throws Exception {
        ObjectNode fixture = hourlyFixture(passage());
        ObjectNode values = (ObjectNode) fixture.path("hourly");
        for (String field : List.of("pm10", "pm2_5", "carbon_monoxide", "nitrogen_dioxide", "sulphur_dioxide", "ozone"))
            values.putArray(field).addNull();
        var provider = provider(fixture.toString());
        assertThat(provider.observationsAt(List.of(query(passage()))).getFirst().observation()).isNull();
        server.stop(0);
        values.putArray("pm2_5").add(0);
        var zero = provider(fixture.toString()).observationsAt(List.of(query(passage()))).getFirst();
        assertThat(zero.observation().pm25()).isZero();
        assertThat(zero.observation().pm10()).isNull();
    }

    @ParameterizedTest @ValueSource(strings = {"malformed", "http", "sizeMismatch"})
    void failedMiddleBatchPreservesFirstAndFinalSingletonBatch(String failure) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/", exchange -> {
            int call = calls.incrementAndGet();
            String item = hourlyFixture(passage()).toString();
            String body = call == 3 ? item : "[" + String.join(",", java.util.Collections.nCopies(100, item)) + "]";
            if (call == 2) body = failure.equals("sizeMismatch") ? "[]" : "not-json";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(call == 2 && failure.equals("http") ? 503 : 200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        var provider = new OpenMeteoEnvironmentalProvider(properties("http://localhost:" + server.getAddress().getPort()), new ObjectMapper());
        var queries = java.util.stream.IntStream.range(0, 201)
                .mapToObj(i -> new EnvironmentalCoordinateQuery(new Coordinate(28.6 + i * .001, 77.2), passage())).toList();
        var results = provider.observationsAt(queries);
        assertThat(results).hasSize(201);
        assertThat(results.stream().filter(r -> r.observation() != null)).hasSize(101);
        assertThat(results.get(100).issues()).isNotEmpty();
        assertThat(results.getLast().observation().pm25()).isEqualTo(5.0);
        assertThat(results.getLast().query()).isEqualTo(queries.getLast());
    }

    @Test void malformedLocationDoesNotDiscardOtherLocationInSameBatch() throws Exception {
        var provider = provider("[{}," + hourlyFixture(passage()) + "]");
        var results = provider.observationsAt(List.of(query(passage()), query(passage())));
        assertThat(results.getFirst().observation()).isNull();
        assertThat(results.get(1).observation().pm25()).isEqualTo(5.0);
    }

    @Test void unsupportedQueryTimeDoesNotDiscardSupportedQuery() throws Exception {
        var provider = provider(hourlyFixture(passage()).toString());
        var results = provider.observationsAt(List.of(query(passage().plus(8, ChronoUnit.DAYS)), query(passage())));
        assertThat(results.getFirst().issues()).contains("UNSUPPORTED_TIME");
        assertThat(results.get(1).observation()).isNotNull();
    }

    private static Instant passage() { return Instant.now().truncatedTo(ChronoUnit.HOURS).plus(2, ChronoUnit.HOURS); }
    private static String hour(Instant time) { return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm").withZone(java.time.ZoneOffset.UTC).format(time); }
    private static EnvironmentalCoordinateQuery query(Instant at) { return new EnvironmentalCoordinateQuery(new Coordinate(28.6, 77.2), at); }
    private static ObjectNode hourlyFixture(Instant at) {
        var mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("latitude", 28.6); root.put("longitude", 77.2); root.put("utc_offset_seconds", 0); root.put("timezone", "GMT");
        ObjectNode units = root.putObject("hourly_units"); units.put("time", "iso8601");
        ObjectNode hourly = root.putObject("hourly"); hourly.putArray("time").add(hour(at));
        String[] names = {"pm10", "pm2_5", "carbon_monoxide", "nitrogen_dioxide", "sulphur_dioxide", "ozone"};
        double[] values = {10, 5, 100, 1, 1, 30};
        for (int i = 0; i < names.length; i++) { units.put(names[i], "μg/m³"); hourly.putArray(names[i]).add(values[i]); }
        return root;
    }

    private OpenMeteoEnvironmentalProvider provider(String body) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return new OpenMeteoEnvironmentalProvider(properties("http://localhost:" + server.getAddress().getPort()), new ObjectMapper());
    }

    private EnvironmentalProperties properties(String url) {
        var properties = new EnvironmentalProperties();
        properties.setOpenMeteoUrl(url);
        properties.setConnectTimeoutMs(500);
        properties.setReadTimeoutMs(500);
        return properties;
    }
    private GeographicCell cell() { return new GeographicCell("cell-a", 28.6, 77.2); }
}
