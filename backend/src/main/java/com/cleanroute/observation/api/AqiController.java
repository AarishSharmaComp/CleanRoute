package com.cleanroute.observation.api;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.repository.ObservationRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.Duration;
import java.util.List;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@RestController
@RequestMapping("/api/aqi")
public class AqiController {
    private static final Map<String, String> POLLUTANT_UNITS = Map.of(
            "aqi", "provider AQI index", "pm25", "µg/m³", "pm10", "µg/m³", "no2", "µg/m³",
            "so2", "µg/m³", "co", "mg/m³", "o3", "µg/m³");
    public record CurrentAqiResponse(String cellId, Instant observedAt, Integer aqi, Double pm25, Double pm10,
            Double no2, Double so2, Double co, Double o3, String provider, boolean generated,
            Map<String, String> units, boolean stale) {}
    public record HistoricalAqiResponse(String cellId, Instant observedAt, Integer aqi, Double pm25, Double pm10,
            Double no2, Double so2, Double co, Double o3, String provider, boolean generated,
            Map<String, String> units) {}
    private final ObservationRepository repository;
    public AqiController(ObservationRepository repository) { this.repository=repository; }
    @GetMapping("/current")
    public CurrentAqiResponse current(@RequestParam(defaultValue="demo-delhi-central") String cell) {
        PollutionObservation o = repository.latest(cell).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No AQI observation for cell"));
        boolean stale = o.observedAt().isBefore(Instant.now().minus(30, ChronoUnit.MINUTES));
        return new CurrentAqiResponse(o.cellId(), o.observedAt(), o.aqi(), o.pm25(), o.pm10(), o.no2(), o.so2(), o.co(), o.o3(), o.provider(), o.generated(), POLLUTANT_UNITS, stale);
    }
    @GetMapping("/history")
    public List<HistoricalAqiResponse> history(@RequestParam String cell,
             @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant start,
             @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) Instant end,
             @RequestParam(defaultValue="15") int interval,
             @RequestParam(defaultValue="100") int limit, @RequestParam(defaultValue="0") int offset) {
        if (end.isBefore(start) || Duration.between(start,end).compareTo(Duration.ofDays(90)) > 0 || interval != 15 || limit < 1 || limit > 1000 || offset < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "History interval must be 15 minutes; use a valid range up to 90 days, limit 1-1000, and nonnegative offset");
        if (!repository.cellExists(cell)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Geographic cell not found");
        return repository.history(cell,start,end,limit,offset).stream().map(AqiController::historyResponse).toList();
    }
    private static HistoricalAqiResponse historyResponse(PollutionObservation o) {
        return new HistoricalAqiResponse(o.cellId(), o.observedAt(), o.aqi(), o.pm25(), o.pm10(), o.no2(), o.so2(), o.co(), o.o3(), o.provider(), o.generated(), POLLUTANT_UNITS);
    }
}
