package com.cleanroute.observation.provider;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.config.RoutingProperties;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Valhalla adapter for genuine OSM pedestrian and bicycle costing. */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.routing.provider", havingValue = "valhalla")
public class ValhallaRoutingProvider implements RoutingProvider {
    private final RoutingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ValhallaRoutingProvider(RoutingProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeout(properties.getConnectTimeoutMs()))).build();
    }

    @Override public String providerId() { return "valhalla"; }

    @Override public RoutePath route(RoutingRequest request) { return alternatives(request).getFirst(); }

    @Override public List<RoutePath> alternatives(RoutingRequest request) {
        if (request == null || request.origin() == null || request.destination() == null
                || request.mode() == TravelMode.CAR || request.mode() == TravelMode.JOG)
            throw ProviderFailureException.unsupported();
        try {
            JsonNode root = mapper.readTree(call(request));
            if (root == null || !"0".equals(root.path("trip").path("status").asText("0")))
                throw ProviderFailureException.temporary();
            List<RoutePath> paths = new ArrayList<>();
            paths.add(normalizeTrip(root.path("trip"), request, "valhalla-primary"));
            JsonNode alternates = root.path("alternates");
            if (alternates.isArray()) for (int i = 0; i < alternates.size(); i++)
                paths.add(normalizeTrip(alternates.get(i).path("trip"), request, "valhalla-" + (i + 2)));
            return List.copyOf(paths);
        } catch (ProviderFailureException failure) { throw failure;
        } catch (HttpTimeoutException timeout) { throw ProviderFailureException.timeout();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw ProviderFailureException.temporary();
        } catch (Exception failure) { throw ProviderFailureException.temporary(); }
    }

    private String call(RoutingRequest request) throws Exception {
        var payload = mapper.createObjectNode();
        var locations = payload.putArray("locations");
        var origin = locations.addObject();
        origin.put("lat", request.origin().latitude()); origin.put("lon", request.origin().longitude()); origin.put("type", "break");
        var destination = locations.addObject();
        destination.put("lat", request.destination().latitude()); destination.put("lon", request.destination().longitude()); destination.put("type", "break");
        payload.put("costing", request.mode() == TravelMode.CYCLE ? "bicycle" : "pedestrian");
        payload.put("units", "kilometers");
        payload.put("alternates", true);
        String base = properties.getValhallaUrl();
        if (base == null || base.isBlank()) throw ProviderFailureException.temporary();
        String encoded = java.net.URLEncoder.encode(mapper.writeValueAsString(payload), java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(trimTrailingSlash(base) + "/route?json=" + encoded))
                .header("Accept", "application/json").header("X-Client-Id", "cleanroute-local")
                .timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs()))).GET().build();
        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofSeconds(60));
        if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderFailureException.temporary();
        return response.body();
    }

    private RoutePath normalizeTrip(JsonNode trip, RoutingRequest request, String id) {
        JsonNode summary = trip.path("summary");
        double distanceKm = summary.path("length").asDouble(Double.NaN);
        double duration = summary.path("time").asDouble(Double.NaN);
        String shape = trip.path("legs").path(0).path("shape").asText("");
        if (!Double.isFinite(distanceKm) || distanceKm <= 0 || !Double.isFinite(duration) || duration < 1
                || shape.isBlank()) throw ProviderFailureException.temporary();
        List<Coordinate> geometry = decodeShape(shape);
        if (geometry.size() < 2 || geometry.size() > properties.getMaxGeometryPoints())
            throw ProviderFailureException.temporary();
        return new RoutePath(geometry, distanceKm * 1000, (int) Math.max(1, Math.ceil(duration)), providerId(), false, id);
    }

    private static List<Coordinate> decodeShape(String encoded) {
        List<Coordinate> points = new ArrayList<>(); int index = 0, lat = 0, lon = 0;
        while (index < encoded.length()) {
            int[] latitude = next(encoded, index); index = latitude[1]; lat += latitude[0];
            int[] longitude = next(encoded, index); index = longitude[1]; lon += longitude[0];
            points.add(new Coordinate(lat / 1e6, lon / 1e6));
        }
        return List.copyOf(points);
    }

    private static int[] next(String value, int start) {
        int result = 0, shift = 0, index = start, digit;
        do { if (index >= value.length()) throw ProviderFailureException.temporary(); digit = value.charAt(index++) - 63; result |= (digit & 0x1f) << shift; shift += 5; } while (digit >= 0x20);
        return new int[]{(result & 1) != 0 ? ~(result >> 1) : result >> 1, index};
    }

    private static String trimTrailingSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private static long timeout(int value) { return Math.max(100, value); }
}
