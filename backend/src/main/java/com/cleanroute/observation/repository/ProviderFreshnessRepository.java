package com.cleanroute.observation.repository;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class ProviderFreshnessRepository {
    public record Freshness(String providerId, String cellId, Instant lastAttemptAt, Instant lastSuccessAt,
                            Instant lastFailureAt, String lastFailureType) {}
    private final JdbcTemplate jdbc;
    public ProviderFreshnessRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void recordSuccess(String providerId, String cellId, Instant at) {
        Timestamp timestamp = Timestamp.from(at);
        int updated = jdbc.update("UPDATE provider_freshness SET last_attempt_at=?,last_success_at=?,last_failure_at=NULL,last_failure_type=NULL WHERE provider_id=? AND cell_id=?",
                timestamp, timestamp, providerId, cellId);
        if (updated == 0) insert(providerId, cellId, timestamp, timestamp, null, null);
    }

    public void recordFailure(String providerId, String cellId, Instant at, String failureType) {
        Timestamp timestamp = Timestamp.from(at);
        int updated = jdbc.update("UPDATE provider_freshness SET last_attempt_at=?,last_failure_at=?,last_failure_type=? WHERE provider_id=? AND cell_id=?",
                timestamp, timestamp, failureType, providerId, cellId);
        if (updated == 0) insert(providerId, cellId, timestamp, null, timestamp, failureType);
    }

    private void insert(String providerId, String cellId, Timestamp attempt, Timestamp success, Timestamp failure, String type) {
        try {
            jdbc.update("INSERT INTO provider_freshness(provider_id,cell_id,last_attempt_at,last_success_at,last_failure_at,last_failure_type) VALUES (?,?,?,?,?,?)",
                    providerId, cellId, attempt, success, failure, type);
        } catch (DuplicateKeyException concurrentInsert) {
            if (success != null) recordSuccess(providerId, cellId, success.toInstant());
            else recordFailure(providerId, cellId, failure.toInstant(), type);
        }
    }

    public Optional<Freshness> find(String providerId, String cellId) {
        return jdbc.query("SELECT provider_id,cell_id,last_attempt_at,last_success_at,last_failure_at,last_failure_type FROM provider_freshness WHERE provider_id=? AND cell_id=?",
                (rs, row) -> new Freshness(rs.getString("provider_id"), rs.getString("cell_id"),
                        rs.getTimestamp("last_attempt_at").toInstant(),
                        rs.getTimestamp("last_success_at") == null ? null : rs.getTimestamp("last_success_at").toInstant(),
                        rs.getTimestamp("last_failure_at") == null ? null : rs.getTimestamp("last_failure_at").toInstant(),
                        rs.getString("last_failure_type")), providerId, cellId).stream().findFirst();
    }
}
