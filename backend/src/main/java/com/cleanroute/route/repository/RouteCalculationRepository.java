package com.cleanroute.route.repository;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RouteCalculationRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public RouteCalculationRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }

    public void save(UUID id, UUID userId, double originLat, double originLon, double destinationLat,
                     double destinationLon, String mode, String preference, String payload) {
        jdbc.update("INSERT INTO route_calculation(id,user_id,origin_latitude,origin_longitude,destination_latitude,destination_longitude,travel_mode,preference,result_payload) VALUES (?,?,?,?,?,?,?,?,?)",
                id, userId, originLat, originLon, destinationLat, destinationLon, mode, preference, payload);
    }

    public void saveHistory(UUID userId, String originName, String destinationName, String mode,
                            String preference, double exposure, int durationSeconds, double distanceMeters) {
        jdbc.update("INSERT INTO route_history(id,user_id,origin_name,destination_name,travel_mode,route_preference,pollution_score,estimated_travel_time_seconds,distance_meters) VALUES (?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), userId, originName, destinationName, mode, preference, exposure, durationSeconds, distanceMeters);
    }

    public Optional<JsonNode> find(UUID id, UUID userId) {
        return jdbc.query("SELECT result_payload FROM route_calculation WHERE id=? AND user_id=?",
                (rs, n) -> {
                    try { return mapper.readTree(rs.getString(1)); }
                    catch (java.io.IOException e) { throw new java.sql.SQLException("Stored route result is invalid", e); }
                }, id, userId).stream().findFirst();
    }
}
