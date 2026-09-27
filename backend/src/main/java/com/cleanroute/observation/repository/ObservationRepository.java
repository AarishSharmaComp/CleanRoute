package com.cleanroute.observation.repository;

import com.cleanroute.observation.domain.ObservationModels.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class ObservationRepository {
    private final JdbcTemplate jdbc;
    public ObservationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void saveCell(GeographicCell c) {
        try { jdbc.update("INSERT INTO geographic_cell(cell_id,center_latitude,center_longitude) VALUES (?,?,?)",
                c.cellId(), c.latitude(), c.longitude()); } catch (DuplicateKeyException ignored) { }
    }
    public boolean cellExists(String cellId) {
        Boolean exists = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM geographic_cell WHERE cell_id=?)", Boolean.class, cellId);
        return Boolean.TRUE.equals(exists);
    }
    public void save(PollutionObservation o) {
        try { jdbc.update("INSERT INTO pollution_observation(id,cell_id,observed_at,aqi,pm25,pm10,no2,so2,co,o3,provider,generated) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID(), o.cellId(), Timestamp.from(o.observedAt()), o.aqi(), o.pm25(), o.pm10(), o.no2(), o.so2(), o.co(), o.o3(), o.provider(), o.generated()); } catch (DuplicateKeyException ignored) { }
    }
    public void save(WeatherObservation o) {
        try { jdbc.update("INSERT INTO weather_observation(id,cell_id,observed_at,temperature_c,humidity_percent,wind_speed_mps,wind_direction_degrees,precipitation_mm,weather_condition,provider,generated) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID(), o.cellId(), Timestamp.from(o.observedAt()), o.temperatureC(), o.humidityPercent(), o.windSpeedMps(), o.windDirectionDegrees(), o.precipitationMm(), o.condition(), o.provider(), o.generated()); } catch (DuplicateKeyException ignored) { }
    }
    public void save(TrafficObservation o) {
        try { jdbc.update("INSERT INTO traffic_observation(id,cell_id,observed_at,traffic_level,congestion_factor,average_speed_kph,provider,generated) VALUES (?,?,?,?,?,?,?,?)",
                java.util.UUID.randomUUID(), o.cellId(), Timestamp.from(o.observedAt()), o.trafficLevel(), o.congestionFactor(), o.averageSpeedKph(), o.provider(), o.generated()); } catch (DuplicateKeyException ignored) { }
    }
    public List<PollutionObservation> history(String cellId, Instant start, Instant end, int limit, int offset) {
        return jdbc.query("SELECT cell_id,observed_at,aqi,pm25,pm10,no2,so2,co,o3,provider,generated FROM pollution_observation WHERE cell_id=? AND observed_at>=? AND observed_at<=? ORDER BY observed_at DESC LIMIT ? OFFSET ?",
                (rs, n) -> new PollutionObservation(rs.getString("cell_id"), rs.getTimestamp("observed_at").toInstant(),
                        (Integer) rs.getObject("aqi"), (Double) rs.getObject("pm25"), (Double) rs.getObject("pm10"),
                        (Double) rs.getObject("no2"), (Double) rs.getObject("so2"), (Double) rs.getObject("co"),
                        (Double) rs.getObject("o3"), rs.getString("provider"), rs.getBoolean("generated")),
                cellId, Timestamp.from(start), Timestamp.from(end), limit, offset);
    }
    public List<PollutionObservation> latest(String cellId) {
        return jdbc.query("SELECT cell_id,observed_at,aqi,pm25,pm10,no2,so2,co,o3,provider,generated FROM pollution_observation WHERE cell_id=? ORDER BY observed_at DESC LIMIT 1",
                (rs, n) -> new PollutionObservation(rs.getString("cell_id"), rs.getTimestamp("observed_at").toInstant(),
                        (Integer) rs.getObject("aqi"), (Double) rs.getObject("pm25"), (Double) rs.getObject("pm10"),
                        (Double) rs.getObject("no2"), (Double) rs.getObject("so2"), (Double) rs.getObject("co"),
                        (Double) rs.getObject("o3"), rs.getString("provider"), rs.getBoolean("generated")), cellId);
    }

    public Optional<PollutionObservation> pollutionAt(String cellId, Instant observedAt) {
        return jdbc.query("SELECT cell_id,observed_at,aqi,pm25,pm10,no2,so2,co,o3,provider,generated FROM pollution_observation WHERE cell_id=? AND observed_at=? ORDER BY generated ASC, ingested_at DESC LIMIT 1",
                (rs, n) -> new PollutionObservation(rs.getString("cell_id"), rs.getTimestamp("observed_at").toInstant(),
                        (Integer) rs.getObject("aqi"), (Double) rs.getObject("pm25"), (Double) rs.getObject("pm10"),
                        (Double) rs.getObject("no2"), (Double) rs.getObject("so2"), (Double) rs.getObject("co"),
                        (Double) rs.getObject("o3"), rs.getString("provider"), rs.getBoolean("generated")),
                cellId, Timestamp.from(observedAt)).stream().findFirst();
    }

    public Optional<WeatherObservation> weatherAt(String cellId, Instant observedAt) {
        return jdbc.query("SELECT cell_id,observed_at,temperature_c,humidity_percent,wind_speed_mps,wind_direction_degrees,precipitation_mm,weather_condition,provider,generated FROM weather_observation WHERE cell_id=? AND observed_at=? ORDER BY generated ASC, ingested_at DESC LIMIT 1",
                (rs, n) -> new WeatherObservation(rs.getString("cell_id"), rs.getTimestamp("observed_at").toInstant(),
                        (Double) rs.getObject("temperature_c"), (Double) rs.getObject("humidity_percent"),
                        (Double) rs.getObject("wind_speed_mps"), (Double) rs.getObject("wind_direction_degrees"),
                        (Double) rs.getObject("precipitation_mm"), rs.getString("weather_condition"),
                        rs.getString("provider"), rs.getBoolean("generated")), cellId, Timestamp.from(observedAt)).stream().findFirst();
    }

    public Optional<TrafficObservation> trafficAt(String cellId, Instant observedAt) {
        return jdbc.query("SELECT cell_id,observed_at,traffic_level,congestion_factor,average_speed_kph,provider,generated FROM traffic_observation WHERE cell_id=? AND observed_at=? ORDER BY generated ASC, ingested_at DESC LIMIT 1",
                (rs, n) -> new TrafficObservation(rs.getString("cell_id"), rs.getTimestamp("observed_at").toInstant(),
                        rs.getString("traffic_level"), (Double) rs.getObject("congestion_factor"),
                        (Double) rs.getObject("average_speed_kph"), rs.getString("provider"), rs.getBoolean("generated")),
                cellId, Timestamp.from(observedAt)).stream().findFirst();
    }
}
