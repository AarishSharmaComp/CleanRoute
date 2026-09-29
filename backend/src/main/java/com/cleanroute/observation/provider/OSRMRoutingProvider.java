package com.cleanroute.observation.provider;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.config.RoutingProperties;
import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
@ConditionalOnProperty(name = "app.routing.provider", havingValue = "osrm")
public class OSRMRoutingProvider implements RoutingProvider {
    private final RoutingProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public OSRMRoutingProvider(RoutingProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout(properties.getConnectTimeoutMs())))
                .build();
    }

    @Override
    public String providerId() { return "osrm"; }

    @Override
    public RoutePath route(RoutingRequest request) {
        if (request == null || request.origin() == null || request.destination() == null)
            throw ProviderFailureException.temporary();
        if (!validCoordinate(request.origin().latitude(), request.origin().longitude())
                || !validCoordinate(request.destination().latitude(), request.destination().longitude()))
            throw ProviderFailureException.temporary();
        String base = properties.getOsrmUrl();
        if (base == null || base.isBlank()) throw ProviderFailureException.temporary();
        String profile = profile(request.mode());
        Coordinate origin = request.origin();
        Coordinate destination = request.destination();
        try {
            URI uri = URI.create(trimTrailingSlash(base) + "/route/v1/" + profile + "/"
                    + coordinate(origin) + ";" + coordinate(destination)
                    + "?overview=full&geometries=geojson&alternatives=true&steps=false");
            HttpRequest httpRequest = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs())))
                    .GET().build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofSeconds(60));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw ProviderFailureException.temporary();
             return normalizeAlternatives(response.body(), request).getFirst();
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

    public RoutePath normalize(String body, RoutingRequest request) {
        return normalizeAlternatives(body, request).getFirst();
    }

    @Override
    public List<RoutePath> alternatives(RoutingRequest request) {
        if (request == null || request.origin() == null || request.destination() == null)
            throw ProviderFailureException.temporary();
        if (!validCoordinate(request.origin().latitude(), request.origin().longitude())
                || !validCoordinate(request.destination().latitude(), request.destination().longitude()))
            throw ProviderFailureException.temporary();
        String base = properties.getOsrmUrl();
        if (base == null || base.isBlank()) throw ProviderFailureException.temporary();
        try {
            String url = trimTrailingSlash(base) + "/route/v1/" + profile(request.mode()) + "/"
                    + coordinate(request.origin()) + ";" + coordinate(request.destination())
                    + "?overview=full&geometries=geojson&alternatives=true&steps=false";
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json")
                    .timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs()))).GET().build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofSeconds(60));
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderFailureException.temporary();
            List<RoutePath> paths = normalizeAlternatives(response.body(), request);
            if (request.mode() != TravelMode.CAR && equivalentToDriving(request, paths))
                throw ProviderFailureException.unsupported();
            return paths;
        } catch (ProviderFailureException failure) { throw failure;
        } catch (HttpTimeoutException timeout) { throw ProviderFailureException.timeout();
        } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw ProviderFailureException.temporary();
        } catch (Exception failure) { throw ProviderFailureException.temporary(); }
    }

    private boolean equivalentToDriving(RoutingRequest request, List<RoutePath> paths) {
        RoutingRequest drivingRequest = new RoutingRequest(request.origin(), request.destination(), TravelMode.CAR);
        try {
            List<RoutePath> driving = fetchAlternatives(drivingRequest);
            return sameRoutes(paths, driving);
        } catch (ProviderFailureException failure) {
            if (failure.getType() == ProviderFailureException.Type.UNSUPPORTED) return true;
            return false;
        }
    }

    private List<RoutePath> fetchAlternatives(RoutingRequest request) {
        try {
            String url = trimTrailingSlash(properties.getOsrmUrl()) + "/route/v1/" + profile(request.mode()) + "/"
                    + coordinate(request.origin()) + ";" + coordinate(request.destination())
                    + "?overview=full&geometries=geojson&alternatives=true&steps=false";
            HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url)).header("Accept", "application/json")
                    .timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs()))).GET().build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) throw ProviderFailureException.unsupported();
            return normalizeAlternatives(response.body(), request);
        } catch (ProviderFailureException failure) { throw failure;
        } catch (Exception failure) { throw ProviderFailureException.temporary(); }
    }

    private static boolean sameRoutes(List<RoutePath> first, List<RoutePath> second) {
        if (first.size() != second.size()) return false;
        for (int i = 0; i < first.size(); i++) {
            RoutePath a = first.get(i), b = second.get(i);
            if (Math.abs(a.distanceMeters() - b.distanceMeters()) > 0.1
                    || Math.abs(a.estimatedDurationSeconds() - b.estimatedDurationSeconds()) > 1
                    || !a.geometry().equals(b.geometry())) return false;
        }
        return true;
    }

    public List<RoutePath> normalizeAlternatives(String body, RoutingRequest request) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !"Ok".equals(root.path("code").asText())
                    || !root.path("routes").isArray() || root.path("routes").size() < 1)
                throw ProviderFailureException.temporary();
            List<RoutePath> paths = new ArrayList<>();
            for (int index = 0; index < root.path("routes").size(); index++) {
                JsonNode route = root.path("routes").get(index);
                double distance = route.path("distance").asDouble(Double.NaN);
                double duration = route.path("duration").asDouble(Double.NaN);
                JsonNode coordinates = route.path("geometry").path("coordinates");
                if (!Double.isFinite(distance) || distance <= 0 || !Double.isFinite(duration)
                        || duration < 1 || duration > Integer.MAX_VALUE || !coordinates.isArray()
                        || coordinates.size() < 2 || coordinates.size() > properties.getMaxGeometryPoints())
                    throw ProviderFailureException.temporary();
                List<Coordinate> geometry = new ArrayList<>();
                for (JsonNode coordinate : coordinates) {
                    if (!coordinate.isArray() || coordinate.size() < 2)
                        throw ProviderFailureException.temporary();
                    double longitude = coordinate.get(0).asDouble(Double.NaN);
                    double latitude = coordinate.get(1).asDouble(Double.NaN);
                    if (!validCoordinate(latitude, longitude)) throw ProviderFailureException.temporary();
                    geometry.add(new Coordinate(latitude, longitude));
                }
                paths.add(new RoutePath(List.copyOf(geometry), distance, (int) Math.max(1, Math.ceil(duration)),
                        providerId(), false, index == 0 ? "osrm-primary" : "osrm-" + (index + 1)));
            }
            return List.copyOf(paths);
        } catch (ProviderFailureException failure) {
            throw failure;
        } catch (Exception malformed) {
            throw ProviderFailureException.temporary();
        }
    }

    private static String profile(TravelMode mode) {
        return mode == TravelMode.WALK || mode == TravelMode.JOG ? "foot"
                : mode == TravelMode.CYCLE ? "cycling" : "driving";
    }

    private static String coordinate(Coordinate coordinate) {
        return Double.toString(coordinate.longitude()) + "," + Double.toString(coordinate.latitude());
    }

    private static String trimTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }

    private static long timeout(int value) { return Math.max(100, value); }
}
