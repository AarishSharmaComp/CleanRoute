package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.*;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class MockWeatherProvider implements WeatherProvider {
    @Override public String providerId() { return "mock-demo-weather"; }
    @Override public WeatherObservation observation(GeographicCell cell, Instant timestamp) {
        long slot = Math.floorDiv(timestamp.getEpochSecond(), 900);
        double phase = (slot % 96) * Math.PI * 2 / 96.0;
        double local = Math.floorMod(cell.cellId().hashCode(), 7);
        double temp = 26 + 5 * Math.sin(phase - Math.PI / 2) + local * .3;
        double humidity = 58 - 15 * Math.sin(phase - Math.PI / 2);
        double wind = 1.8 + Math.floorMod((int) slot + cell.cellId().hashCode(), 25) / 10.0;
        double precip = Math.floorMod((int) (slot / 96 + cell.cellId().hashCode()), 17) == 0 ? .4 : 0;
        String condition = precip > 0 ? "LIGHT_RAIN" : temp > 29 ? "CLEAR" : "PARTLY_CLOUDY";
        return new WeatherObservation(cell.cellId(), timestamp, Math.round(temp * 10) / 10.0,
                (double) Math.round(humidity), wind, (double) Math.floorMod((int) slot * 7, 360), precip,
                condition, providerId(), true);
    }
}
