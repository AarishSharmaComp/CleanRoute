package com.cleanroute.geocoding.provider;

import com.cleanroute.geocoding.config.GeocodingProperties;
import com.cleanroute.geocoding.domain.GeocodingModels.LocationSearchResult;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.cleanroute.observation.repository.ObservationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class PhotonGeocodingProvider implements GeocodingProvider {
    private static final Logger log = LoggerFactory.getLogger(PhotonGeocodingProvider.class);
    private final GeocodingProperties properties;
    private final ObservationRepository observations;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public PhotonGeocodingProvider(GeocodingProperties properties,
                                   ObservationRepository observations,
                                   ObjectMapper mapper) {
        this.properties = properties;
        this.observations = observations;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout(properties.getConnectTimeoutMs())))
                .build();
    }

    @Override
    public String providerId() {
        return "photon-geocoding";
    }

    @Override
    public List<LocationSearchResult> search(String query) {
        String normalized = query == null ? "" : query.trim();
        if (normalized.length() < 3) return List.of();

        String base = properties.getPhotonUrl();
        if (base == null || base.isBlank()) throw ProviderFailureException.temporary();
        if (!base.endsWith("/")) base += "/";
        URI uri;
        try {
            uri = URI.create(base + "?q=" + URLEncoder.encode(normalized, StandardCharsets.UTF_8)
                    + "&limit=6&lang=en");
        } catch (IllegalArgumentException malformedUrl) {
            throw ProviderFailureException.temporary();
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .header("Accept", "application/json")
                    .timeout(Duration.ofMillis(timeout(properties.getReadTimeoutMs())))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw ProviderFailureException.rateLimited(Duration.ofSeconds(60));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("Photon geocoder returned HTTP status {}", response.statusCode());
                throw ProviderFailureException.temporary();
            }
            return normalize(response.body());
        } catch (ProviderFailureException failure) {
            throw failure;
        } catch (HttpTimeoutException timeout) {
            log.warn("Photon geocoder request timed out");
            throw ProviderFailureException.timeout();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw ProviderFailureException.temporary();
        } catch (Exception failure) {
            log.warn("Photon geocoder request failed ({})", failure.getClass().getSimpleName());
            throw ProviderFailureException.temporary();
        }
    }

    public List<LocationSearchResult> normalize(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root == null || !root.isObject() || !root.path("features").isArray())
                throw ProviderFailureException.temporary();

            List<GeographicCell> cells = observations.cells();
            List<LocationSearchResult> results = new ArrayList<>();
            for (JsonNode feature : root.path("features")) {
                JsonNode coordinates = feature.path("geometry").path("coordinates");
                if (!coordinates.isArray() || coordinates.size() < 2) continue;
                double longitude = coordinates.get(0).asDouble(Double.NaN);
                double latitude = coordinates.get(1).asDouble(Double.NaN);
                if (!validCoordinate(latitude, longitude)) continue;

                JsonNode properties = feature.path("properties");
                String name = text(properties, "name");
                if (name == null) continue;
                List<String> labels = new ArrayList<>();
                for (String key : List.of("district", "city", "county", "state", "country", "postcode")) {
                    String value = text(properties, key);
                    if (value != null && !value.equalsIgnoreCase(name) && !labels.contains(value)) labels.add(value);
                }
                String context = String.join(", ", labels);
                String displayName = context.isBlank() ? name : name + ", " + context;
                boolean supportedArea = isWithinSupportedArea(latitude, longitude, cells, properties().getCoverageRadiusMeters());
                LocationSearchResult result = new LocationSearchResult(name, displayName, context, latitude, longitude, supportedArea);
                if (results.stream().noneMatch(existing -> existing.displayName().equalsIgnoreCase(result.displayName())
                        && Math.abs(existing.latitude() - result.latitude()) < 0.0001
                        && Math.abs(existing.longitude() - result.longitude()) < 0.0001)) results.add(result);
                if (results.size() == 6) break;
            }
            return List.copyOf(results);
        } catch (ProviderFailureException failure) {
            throw failure;
        } catch (Exception malformed) {
            throw ProviderFailureException.temporary();
        }
    }

    private GeocodingProperties properties() { return properties; }

    private static boolean validCoordinate(double latitude, double longitude) {
        return Double.isFinite(latitude) && latitude >= -90 && latitude <= 90
                && Double.isFinite(longitude) && longitude >= -180 && longitude <= 180;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isValueNode()) return null;
        String text = value.asText().trim();
        return text.isBlank() ? null : text;
    }

    public static boolean isWithinSupportedArea(double latitude, double longitude,
                                                List<GeographicCell> cells, double radiusMeters) {
        return cells != null && cells.stream().anyMatch(cell -> haversine(latitude, longitude,
                cell.latitude(), cell.longitude()) <= radiusMeters);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static long timeout(int value) { return Math.max(100, value); }
}
