package com.cleanroute.pollution.repository;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PollutionForecastRepository {
    private final JdbcTemplate jdbc;
    public PollutionForecastRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean cellExists(String cell) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM geographic_cell WHERE cell_id=?)", Boolean.class, cell));
    }

    public List<PollutionObservation> observations(String cell, Instant from, Instant to, int limit) {
        return jdbc.query("SELECT cell_id,observed_at,aqi,pm25,pm10,no2,so2,co,o3,provider,generated FROM pollution_observation WHERE cell_id=? AND observed_at>=? AND observed_at<=? ORDER BY observed_at ASC LIMIT ?",
                (rs, n) -> new PollutionObservation(rs.getString("cell_id"), rs.getTimestamp("observed_at").toInstant(),
                        (Integer) rs.getObject("aqi"), (Double) rs.getObject("pm25"), (Double) rs.getObject("pm10"),
                        (Double) rs.getObject("no2"), (Double) rs.getObject("so2"), (Double) rs.getObject("co"),
                        (Double) rs.getObject("o3"), rs.getString("provider"), rs.getBoolean("generated")),
                cell, Timestamp.from(from), Timestamp.from(to), limit);
    }

    public void save(PollutionForecast f) {
        int updated = jdbc.update("UPDATE pollution_forecast SET generated_at=?,aqi=?,pm25=?,pm10=?,no2=?,so2=?,co=?,o3=?,quality_score=?,quality=?,sample_count=?,provider=?,source_generated=? WHERE cell_id=? AND target_at=? AND model_version=?",
                Timestamp.from(f.generatedAt()), f.aqi(), f.pm25(), f.pm10(), f.no2(), f.so2(), f.co(), f.o3(), f.qualityScore(), f.quality(), f.sampleCount(), f.provider(), f.sourceGenerated(), f.cellId(), Timestamp.from(f.targetAt()), f.modelVersion());
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO pollution_forecast(cell_id,target_at,generated_at,aqi,pm25,pm10,no2,so2,co,o3,quality_score,quality,sample_count,provider,model_version,source_generated) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                        f.cellId(), Timestamp.from(f.targetAt()), Timestamp.from(f.generatedAt()), f.aqi(), f.pm25(), f.pm10(), f.no2(), f.so2(), f.co(), f.o3(), f.qualityScore(), f.quality(), f.sampleCount(), f.provider(), f.modelVersion(), f.sourceGenerated());
            } catch (DuplicateKeyException race) {
                save(f);
            }
        }
    }

    public Optional<PollutionForecast> find(String cell, Instant target) {
        return jdbc.query("SELECT * FROM pollution_forecast WHERE cell_id=? AND target_at=? ORDER BY generated_at DESC LIMIT 1",
                this::map, cell, Timestamp.from(target)).stream().findFirst();
    }

    public List<PollutionForecast> history(String cell, Instant from, Instant to, int limit) {
        return jdbc.query("SELECT * FROM pollution_forecast WHERE cell_id=? AND target_at>=? AND target_at<=? ORDER BY target_at ASC LIMIT ?",
                this::map, cell, Timestamp.from(from), Timestamp.from(to), limit);
    }

    private PollutionForecast map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new PollutionForecast(rs.getString("cell_id"), rs.getTimestamp("target_at").toInstant(),
                rs.getTimestamp("generated_at").toInstant(), (Integer) rs.getObject("aqi"),
                (Double) rs.getObject("pm25"), (Double) rs.getObject("pm10"), (Double) rs.getObject("no2"),
                (Double) rs.getObject("so2"), (Double) rs.getObject("co"), (Double) rs.getObject("o3"),
                rs.getInt("quality_score"), rs.getString("quality"), rs.getInt("sample_count"),
                rs.getString("provider"), rs.getString("model_version"), rs.getBoolean("source_generated"));
    }
}
