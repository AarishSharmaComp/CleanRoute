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

    private static RoutingProperties properties(String url) {
        RoutingProperties configured = new RoutingProperties();
        configured.setOsrmUrl(url);
        return configured;
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
