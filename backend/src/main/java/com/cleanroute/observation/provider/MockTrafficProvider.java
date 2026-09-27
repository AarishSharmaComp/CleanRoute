package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.ObservationModels.*;
import org.springframework.stereotype.Component;
import java.time.Instant;

@Component
public class MockTrafficProvider implements TrafficProvider {
    @Override public String providerId() { return "mock-demo-traffic"; }
    @Override public TrafficObservation observation(GeographicCell cell, Instant timestamp) {
        long slot = Math.floorDiv(timestamp.getEpochSecond(), 900);
        int hour = (int) (slot % 96) / 4;
        boolean rush = hour >= 7 && hour <= 10 || hour >= 16 && hour <= 19;
        double factor = (rush ? 2.7 : 1.3) + Math.floorMod(cell.cellId().hashCode(), 9) / 10.0;
        factor = Math.min(5, factor);
        String level = factor >= 4 ? "HEAVY" : factor >= 2.5 ? "MODERATE" : "LOW";
        return new TrafficObservation(cell.cellId(), timestamp, level, factor,
                Math.round((48 / factor) * 10) / 10.0, providerId(), true);
    }
}
