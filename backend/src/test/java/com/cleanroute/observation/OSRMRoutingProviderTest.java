package com.cleanroute.observation;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.config.RoutingProperties;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.OSRMRoutingProvider;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.stream.IntStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OSRMRoutingProviderTest {
    private final RoutingProperties properties = new RoutingProperties();
    private final OSRMRoutingProvider provider = new OSRMRoutingProvider(properties, new ObjectMapper());
    private final RoutingRequest request = new RoutingRequest(new Coordinate(28.6139, 77.2090),
            new Coordinate(28.7041, 77.1025), TravelMode.WALK);

    @Test void normalizesRoadGeometryDistanceDurationAndProviderMetadata() {
        RoutePath path = provider.normalize("""
                {"code":"Ok","routes":[{"distance":12345.6,"duration":987.2,
                "geometry":{"type":"LineString","coordinates":[[77.209,28.6139],[77.18,28.65],[77.1025,28.7041]]}}]}
                """, request);

        assertThat(path.provider()).isEqualTo("osrm");
        assertThat(path.generated()).isFalse();
        assertThat(path.alternativeId()).isEqualTo("osrm-primary");
        assertThat(path.distanceMeters()).isEqualTo(12345.6);
        assertThat(path.estimatedDurationSeconds()).isEqualTo(988);
        assertThat(path.geometry()).containsExactly(
                new Coordinate(28.6139, 77.209), new Coordinate(28.65, 77.18), new Coordinate(28.7041, 77.1025));
    }

    @Test void normalizesEveryReturnedAlternativeWithoutFabricatingRoutes() {
        List<RoutePath> paths = provider.normalizeAlternatives("""
                {"code":"Ok","routes":[
                {"distance":1000,"duration":60,"geometry":{"coordinates":[[77.2,28.6],[77.21,28.61]]}},
                {"distance":1200,"duration":75,"geometry":{"coordinates":[[77.2,28.6],[77.205,28.615],[77.21,28.61]]}},
                {"distance":1400,"duration":90,"geometry":{"coordinates":[[77.2,28.6],[77.215,28.605],[77.21,28.61]]}}
                ]}
                """, request);

        assertThat(paths).hasSize(3);
        assertThat(paths).extracting(RoutePath::alternativeId).containsExactly("osrm-primary", "osrm-2", "osrm-3");
        assertThat(paths).extracting(RoutePath::distanceMeters).containsExactly(1000.0, 1200.0, 1400.0);
        assertThat(paths.get(1).geometry()).hasSize(3);
    }

    @Test void preservesOneRouteResponseAsOneHonestCandidate() {
        assertThat(provider.normalizeAlternatives("""
                {"code":"Ok","routes":[{"distance":1000,"duration":60,
                "geometry":{"coordinates":[[77.2,28.6],[77.21,28.61]]}}]}
                """, request)).hasSize(1);
    }

    @Test void rejectsMalformedOrUnusableResponsesWithoutFabricatingRouteData() {
        assertThatThrownBy(() -> provider.normalize("not-json", request))
                .isInstanceOf(ProviderFailureException.class);
        assertThatThrownBy(() -> provider.normalize("{\"code\":\"Ok\",\"routes\":[]}", request))
                .isInstanceOf(ProviderFailureException.class);
        assertThatThrownBy(() -> provider.normalize("""
                {"code":"Ok","routes":[{"distance":1,"duration":1,
                "geometry":{"coordinates":[[200,28.6],[77.2,28.7]]}}]}
                """, request)).isInstanceOf(ProviderFailureException.class);
    }

    @Test void preservesSeventyNinePointGeometryAndProviderMetrics() {
        RoutePath path = provider.normalize(responseWithPoints(79, 2500.9, 287.8), request);

        assertThat(path.geometry()).hasSize(79);
        assertThat(path.geometry().get(0)).isEqualTo(new Coordinate(28.6, 77.2));
        assertThat(path.geometry().get(78)).isEqualTo(new Coordinate(28.678, 77.278));
        assertThat(path.distanceMeters()).isEqualTo(2500.9);
        assertThat(path.estimatedDurationSeconds()).isEqualTo(288);
    }

    @Test void preservesSixHundredEightyThreePointGeometryAndProviderMetrics() {
        RoutePath path = provider.normalize(responseWithPoints(683, 51930.5, 3337.5), request);

        assertThat(path.geometry()).hasSize(683);
        assertThat(path.geometry().get(0)).isEqualTo(new Coordinate(28.6, 77.2));
        assertThat(path.geometry().get(682)).isEqualTo(new Coordinate(29.282, 77.882));
        assertThat(path.distanceMeters()).isEqualTo(51930.5);
        assertThat(path.estimatedDurationSeconds()).isEqualTo(3338);
    }

    @Test void rejectsGeometryAboveConfiguredSafetyLimit() {
        RoutingProperties configured = new RoutingProperties();
        configured.setMaxGeometryPoints(100);
        OSRMRoutingProvider limited = new OSRMRoutingProvider(configured, new ObjectMapper());

        assertThatThrownBy(() -> limited.normalize(responseWithPoints(101, 1000, 100), request))
                .isInstanceOf(ProviderFailureException.class);
    }

    @Test void mapsHttpFailureToProviderFailureWithoutReturningSyntheticData() throws IOException {
        HttpServer server = server(500, "failure", 0);
        try {
            OSRMRoutingProvider configuredProvider = new OSRMRoutingProvider(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()), new ObjectMapper());
            assertThatThrownBy(() -> configuredProvider.route(request)).isInstanceOf(ProviderFailureException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test void mapsNetworkFailureToProviderFailure() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        int port = server.getAddress().getPort();
        server.stop(0);
        OSRMRoutingProvider configuredProvider = new OSRMRoutingProvider(
                properties("http://127.0.0.1:" + port), new ObjectMapper());
        assertThatThrownBy(() -> configuredProvider.route(request)).isInstanceOf(ProviderFailureException.class);
    }

    @Test void mapsTimeoutToProviderFailure() throws IOException {
        HttpServer server = server(200, "{}", 300);
        try {
            RoutingProperties configured = properties("http://127.0.0.1:" + server.getAddress().getPort());
            configured.setReadTimeoutMs(100);
            OSRMRoutingProvider configuredProvider = new OSRMRoutingProvider(configured, new ObjectMapper());
            assertThatThrownBy(() -> configuredProvider.route(request)).isInstanceOf(ProviderFailureException.class);
        } finally {
            server.stop(0);
        }
    }

    @Test void requestsFullGeoJsonGeometryWithModeSpecificProfile() throws IOException {
        AtomicReference<String> requestPath = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().toString());
            byte[] body = "{\"code\":\"Ok\",\"routes\":[{\"distance\":1000,\"duration\":600,\"geometry\":{\"type\":\"LineString\",\"coordinates\":[[77.2,28.6],[77.21,28.61]]}}]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OSRMRoutingProvider configuredProvider = new OSRMRoutingProvider(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()), new ObjectMapper());
            configuredProvider.route(new RoutingRequest(new Coordinate(28.6, 77.2),
                    new Coordinate(28.61, 77.21), TravelMode.CYCLE));
            assertThat(requestPath).hasValueSatisfying(path -> assertThat(path)
                    .startsWith("/route/v1/cycling/77.2,28.6;77.21,28.61")
                    .contains("overview=full")
                    .contains("geometries=geojson")
                     .contains("alternatives=true")
                    .contains("steps=false"));
        } finally {
            server.stop(0);
        }
    }

    @Test void rejectsNonCarProfileWhenProviderReturnsTheDrivingRoutesVerbatim() throws IOException {
        AtomicReference<String> requestPaths = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPaths.updateAndGet(previous -> previous + "\n" + exchange.getRequestURI());
            byte[] body = responseWithPoints(2, 1000, 60).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OSRMRoutingProvider configuredProvider = new OSRMRoutingProvider(
                    properties("http://127.0.0.1:" + server.getAddress().getPort()), new ObjectMapper());
            assertThatThrownBy(() -> configuredProvider.alternatives(request))
                    .isInstanceOf(ProviderFailureException.class)
                    .extracting("type").isEqualTo(ProviderFailureException.Type.UNSUPPORTED);
            assertThat(requestPaths).hasValueSatisfying(paths -> assertThat(paths)
                    .contains("/route/v1/foot/").contains("/route/v1/driving/"));
        } finally {
            server.stop(0);
        }
    }

    private static RoutingProperties properties(String url) {
        RoutingProperties configured = new RoutingProperties();
        configured.setOsrmUrl(url);
        return configured;
    }

    private static String responseWithPoints(int count, double distance, double duration) {
        String coordinates = IntStream.range(0, count)
                .mapToObj(index -> "[" + (77.2 + index / 1000.0) + "," + (28.6 + index / 1000.0) + "]")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"code\":\"Ok\",\"routes\":[{\"distance\":" + distance
                + ",\"duration\":" + duration
                + ",\"geometry\":{\"type\":\"LineString\",\"coordinates\":[" + coordinates + "]}}]}";
    }

    private static HttpServer server(int status, String body, long delayMillis) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                if (delayMillis > 0) Thread.sleep(delayMillis);
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
                exchange.getResponseBody().close();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                exchange.close();
            }
        });
        server.start();
        return server;
    }
}
