package com.cleanroute.observation;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.config.RoutingProperties;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.observation.provider.ValhallaRoutingProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValhallaRoutingProviderTest {
    private final RoutingRequest walk = new RoutingRequest(new Coordinate(28.6, 77.2),
            new Coordinate(28.61, 77.21), TravelMode.WALK);

    @Test void usesPedestrianCostingPreservesDecodedGeometryAndAlternatives() throws Exception {
        String body = response("pedestrian", 49.561, 36118.98, 51.176, 36200);
        HttpServer server = server(body);
        try {
            RoutingProperties properties = new RoutingProperties();
            properties.setValhallaUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var paths = new ValhallaRoutingProvider(properties, new ObjectMapper()).alternatives(walk);
            assertThat(paths).hasSize(2);
            assertThat(paths.getFirst().alternativeId()).isEqualTo("valhalla-primary");
            assertThat(paths.getFirst().distanceMeters()).isEqualTo(49561.0);
            assertThat(paths.getFirst().estimatedDurationSeconds()).isEqualTo(36119);
            assertThat(paths.getFirst().geometry()).containsExactly(
                    new Coordinate(28.6, 77.2), new Coordinate(28.61, 77.21));
            assertThat(paths.get(1).alternativeId()).isEqualTo("valhalla-2");
        } finally { server.stop(0); }
    }

    @Test void usesBicycleCostingAndRejectsCarAndJogRequests() throws Exception {
        HttpServer server = server(response("bicycle", 51.063, 10659.08, 51.176, 10673.145));
        try {
            RoutingProperties properties = new RoutingProperties();
            properties.setValhallaUrl("http://127.0.0.1:" + server.getAddress().getPort());
            var provider = new ValhallaRoutingProvider(properties, new ObjectMapper());
            var request = new RoutingRequest(walk.origin(), walk.destination(), TravelMode.CYCLE);
            assertThat(provider.alternatives(request)).extracting(RoutePath::distanceMeters)
                    .containsExactly(51063.0, 51176.0);
            assertThatThrownBy(() -> provider.route(new RoutingRequest(walk.origin(), walk.destination(), TravelMode.CAR)))
                    .isInstanceOf(RuntimeException.class);
        } finally { server.stop(0); }
    }

    private static HttpServer server(String body) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/route", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start(); return server;
    }

    private static String response(String costing, double distance, double time, double altDistance, double altTime) {
        String shape = encode(new Coordinate[]{new Coordinate(28.6, 77.2), new Coordinate(28.61, 77.21)});
        return "{\"trip\":{\"status\":0,\"summary\":{\"length\":" + distance + ",\"time\":" + time
                + "},\"legs\":[{\"shape\":\"" + shape + "\"}]},\"alternates\":[{\"trip\":{\"status\":0,\"summary\":{\"length\":"
                + altDistance + ",\"time\":" + altTime + "},\"legs\":[{\"shape\":\"" + shape + "\"}]}}]}";
    }

    private static String encode(Coordinate[] points) {
        StringBuilder result = new StringBuilder(); int lastLat = 0, lastLon = 0;
        for (Coordinate point : points) {
            int lat = (int) Math.round(point.latitude() * 1_000_000);
            int lon = (int) Math.round(point.longitude() * 1_000_000);
            append(result, lat - lastLat); append(result, lon - lastLon); lastLat = lat; lastLon = lon;
        }
        return result.toString();
    }

    private static void append(StringBuilder result, int value) {
        int encoded = value < 0 ? ~(value << 1) : value << 1;
        while (encoded >= 0x20) { result.append((char) (((encoded & 0x1f) | 0x20) + 63)); encoded >>= 5; }
        result.append((char) (encoded + 63));
    }
}
