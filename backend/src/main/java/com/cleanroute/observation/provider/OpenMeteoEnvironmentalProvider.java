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

@Component
@ConditionalOnProperty(name = "app.environmental.provider", havingValue = "open-meteo")
public class OpenMeteoEnvironmentalProvider implements EnvironmentalDataProvider {
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

    @Override
    public List<PollutionObservation> observations(GeographicCell cell, Instant ignoredTimestamp) {
        if (cell == null || !valid(cell.latitude(), cell.longitude())) throw ProviderFailureException.temporary();
        try {
            String query = "?latitude=" + cell.latitude() + "&longitude=" + cell.longitude()
                    + "&current=pm10,pm2_5,carbon_monoxide,nitrogen_dioxide,sulphur_dioxide,ozone&timezone=UTC";
            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getOpenMeteoUrl() + query))
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
