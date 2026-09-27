package com.cleanroute.pollution.api;

import com.cleanroute.pollution.domain.ForecastModels.ForecastResponse;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.pollution.service.PollutionForecastService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/pollution/forecast")
public class PollutionForecastController {
    private static final String LIMITATION = "Historical pattern baseline only; not an advanced or validated forecast model.";
    private final PollutionForecastService forecasts;

    public PollutionForecastController(PollutionForecastService forecasts) { this.forecasts = forecasts; }

    @GetMapping
    public List<ForecastResponse> forecast(
            @RequestParam String cell,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(defaultValue = "15") int interval,
            @RequestParam(defaultValue = "4") @Min(1) @Max(96) int count) {
        return forecasts.forecastSeries(cell, from, interval, count).stream().map(this::response).toList();
    }

    @GetMapping("/history")
    public List<ForecastResponse> history(
            @RequestParam String cell,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "100") @Min(1) @Max(1000) int limit) {
        return forecasts.history(cell, from, to, limit).stream().map(this::response).toList();
    }

    private ForecastResponse response(PollutionForecast forecast) {
        return new ForecastResponse("PREDICTED", false, true, forecast, LIMITATION);
    }
}
