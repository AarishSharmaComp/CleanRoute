package com.cleanroute.observation;

import com.cleanroute.observation.config.EnvironmentalProperties;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.provider.OpenMeteoEnvironmentalProvider;
import com.cleanroute.observation.provider.ProviderFailureException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenMeteoEnvironmentalProviderTest {
    private HttpServer server;

    @AfterEach void stopServer() { if (server != null) server.stop(0); }

    @Test void normalizesDocumentedCurrentMeasurementsAndLeavesAqiAbsent() throws Exception {
        var provider = provider("""
                {"current_units":{"time":"iso8601","pm10":"μg/m³","pm2_5":"μg/m³","carbon_monoxide":"μg/m³","nitrogen_dioxide":"μg/m³","sulphur_dioxide":"μg/m³","ozone":"μg/m³"},
                 "current":{"time":"2026-09-29T08:00:00Z","pm10":102.4,"pm2_5":30.0,"carbon_monoxide":406.0,"nitrogen_dioxide":5.3,"sulphur_dioxide":18.2,"ozone":168.0}}
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

    @Test void rejectsUnexpectedUnits() {
        var provider = new OpenMeteoEnvironmentalProvider(properties(""), new ObjectMapper());
        assertThatThrownBy(() -> provider.normalize("""
                {"current_units":{"pm10":"mg/m³","pm2_5":"μg/m³","carbon_monoxide":"μg/m³","nitrogen_dioxide":"μg/m³","sulphur_dioxide":"μg/m³","ozone":"μg/m³"},
                 "current":{"time":"2026-09-29T08:00:00Z","pm10":1,"pm2_5":1,"carbon_monoxide":1,"nitrogen_dioxide":1,"sulphur_dioxide":1,"ozone":1}}
                """, cell())).isInstanceOf(ProviderFailureException.class);
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
