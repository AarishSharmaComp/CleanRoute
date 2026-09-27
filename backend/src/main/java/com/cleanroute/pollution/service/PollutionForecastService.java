package com.cleanroute.pollution.service;

import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.pollution.domain.ForecastModels.ForecastPrediction;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.pollution.repository.PollutionForecastRepository;
import com.cleanroute.pollution.provider.ForecastProvider;
import com.cleanroute.pollution.provider.ForecastProvider.ForecastUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class PollutionForecastService {
    private static final int MAX_HISTORY_ROWS = 10000;
    private static final Duration HISTORY_WINDOW = Duration.ofDays(90);
    private final PollutionForecastRepository repository;
    private final ForecastProvider provider;

    public PollutionForecastService(PollutionForecastRepository repository, ForecastProvider provider) {
        this.repository = repository;
        this.provider = provider;
    }

    public PollutionForecast forecast(String cell, Instant target) {
        if (!repository.cellExists(cell)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Geographic cell not found");
        if (target == null || Math.floorMod(target.getEpochSecond(), 900) != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast timestamp must align to a 15-minute boundary");
        if (!target.isAfter(Instant.now())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast target must be in the future");
        if (target.isAfter(Instant.now().plus(Duration.ofDays(30))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast target cannot be more than 30 days ahead");
        Instant from = target.minus(HISTORY_WINDOW);
        List<PollutionObservation> history = repository.observations(cell, from, target, MAX_HISTORY_ROWS);
        ForecastPrediction prediction;
        try { prediction = provider.predict(cell, target, history); }
        catch (ForecastUnavailableException missing) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient pollution history to produce a forecast");
        }
        PollutionForecast result = new PollutionForecast(cell, target, Instant.now(), prediction.aqi(), prediction.pm25(),
                prediction.pm10(), prediction.no2(), prediction.so2(), prediction.co(), prediction.o3(),
                prediction.qualityScore(), prediction.quality(), prediction.sampleCount(), provider.providerId(),
                prediction.modelVersion(), prediction.sourceGenerated());
        repository.save(result);
        return result;
    }

    public List<PollutionForecast> forecastSeries(String cell, Instant from, int intervalMinutes, int count) {
        if (intervalMinutes != 15) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast interval must be 15 minutes");
        if (count < 1 || count > 96) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast count must be between 1 and 96");
        if (from == null || Math.floorMod(from.getEpochSecond(), 900) != 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast start must align to a 15-minute boundary");
        Instant now = Instant.now();
        if (!from.isAfter(now)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast start must be in the future");
        Instant first = from;
        if (first.plus(15L * (count - 1), ChronoUnit.MINUTES).isAfter(now.plus(Duration.ofDays(30))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast range cannot extend more than 30 days ahead");
        java.util.ArrayList<PollutionForecast> results = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) results.add(forecast(cell, first.plus(15L * i, ChronoUnit.MINUTES)));
        return List.copyOf(results);
    }

    public List<PollutionForecast> history(String cell, Instant from, Instant to, int limit) {
        if (!repository.cellExists(cell)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Geographic cell not found");
        if (from == null || to == null || from.isAfter(to) || Duration.between(from, to).compareTo(Duration.ofDays(90)) > 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Forecast history range must be ordered and no longer than 90 days");
        if (limit < 1 || limit > 1000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Limit must be between 1 and 1000");
        return repository.history(cell, from, to, limit);
    }
}
