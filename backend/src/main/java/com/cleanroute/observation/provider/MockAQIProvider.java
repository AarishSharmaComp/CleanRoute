package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.*;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;

@Component
public class MockAQIProvider implements AQIProvider {
    @Override public String providerId() { return "mock-demo-aqi"; }
    @Override public List<PollutionObservation> observations(GeographicCell cell, Instant timestamp) {
        long slot = Math.floorDiv(timestamp.getEpochSecond(), 900);
        double day = Math.sin((slot % 96) * Math.PI * 2 / 96.0);
        double week = Math.cos((slot / 96 % 7) * Math.PI * 2 / 7.0);
        double offset = Math.floorMod(cell.cellId().hashCode(), 13);
        int aqi = (int) Math.round(72 + offset + 18 * day + 8 * week);
        return List.of(new PollutionObservation(cell.cellId(), timestamp, aqi, round(18 + offset * .7 + 5 * day),
                round(38 + offset + 9 * day), round(20 + offset * .4 + 4 * day), null, null,
                round(28 + offset * .5 - 4 * day), providerId(), true));
    }
    private static double round(double n) { return Math.round(n * 10) / 10.0; }
}
