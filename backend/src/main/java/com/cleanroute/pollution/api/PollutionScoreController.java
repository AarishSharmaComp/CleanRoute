package com.cleanroute.pollution.api;

import com.cleanroute.domain.TravelMode;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.domain.PollutionScoreModels.PollutionScore;
import com.cleanroute.pollution.service.PollutionEngine;
import com.cleanroute.pollution.service.PollutionScoreService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Validated
@RestController
@RequestMapping("/api/pollution")
public class PollutionScoreController {
    private final ObservationRepository observations;
    private final PollutionScoreService scores;

    public PollutionScoreController(ObservationRepository observations, PollutionScoreService scores) {
        this.observations = observations;
        this.scores = scores;
    }

    @GetMapping("/score")
    public PollutionScore score(@RequestParam(required = false) @NotNull String cell,
                                @RequestParam(required = false) @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at,
                                @RequestParam(required = false) @NotNull @Min(1) @Max(86400) Integer durationSeconds,
                                @RequestParam(required = false) @NotNull @DecimalMin("0.001") @DecimalMax("200000") Double distanceMeters,
                                @RequestParam(required = false) @NotNull TravelMode mode) {
        Instant now = Instant.now();
        if (at.isAfter(now) || at.isBefore(now.minus(Duration.ofDays(90))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scoring time must be within the previous 90 days and not in the future");
        Instant interval = Instant.ofEpochSecond(Math.floorDiv(at.getEpochSecond(), 900) * 900);
        if (!interval.equals(at))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Scoring time must align to a 15-minute observation interval");
        if (!observations.cellExists(cell)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Geographic cell not found");

        var pollution = observations.pollutionAt(cell, at).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "No pollution observation for this cell and interval"));
        var weather = observations.weatherAt(cell, at).orElse(null);
        var traffic = observations.trafficAt(cell, at).orElse(null);
        try {
            return scores.score(pollution, weather, traffic, durationSeconds, distanceMeters, mode);
        } catch (PollutionEngine.InsufficientPollutionDataException insufficient) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, insufficient.getMessage());
        } catch (IllegalArgumentException invalid) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Observation inputs are invalid for scoring");
        }
    }
}
