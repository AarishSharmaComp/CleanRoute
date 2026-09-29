package com.cleanroute.route.service;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import java.util.ArrayList;
import java.util.List;

/** Deterministically samples route geometry by distance while retaining both endpoints. */
public final class RouteSampler {
    private static final double EARTH_RADIUS_METERS = 6_371_000;
    private RouteSampler() {}

    public record Sample(Coordinate coordinate, double distanceFromStartMeters) {}

    public static List<Sample> sample(List<Coordinate> geometry, int intervalMeters) {
        if (geometry == null || geometry.size() < 2 || intervalMeters < 1)
            throw new IllegalArgumentException("Route geometry and sampling limits are invalid");
        List<Double> legLengths = new ArrayList<>();
        double total = 0;
        for (int i = 1; i < geometry.size(); i++) {
            double length = haversine(geometry.get(i - 1), geometry.get(i));
            legLengths.add(length); total += length;
        }
        if (!Double.isFinite(total) || total <= 0) return List.of(new Sample(geometry.getFirst(), 0),
                new Sample(geometry.getLast(), 0));
        int desired = Math.max(2, (int) Math.ceil(total / intervalMeters) + 1);
        List<Sample> samples = new ArrayList<>(desired);
        for (int i = 0; i < desired; i++) {
            double target = Math.min(total, (double) i * intervalMeters);
            samples.add(new Sample(atDistance(geometry, legLengths, target), target));
        }
        return List.copyOf(samples);
    }

    private static Coordinate atDistance(List<Coordinate> geometry, List<Double> legs, double target) {
        double traversed = 0;
        for (int i = 0; i < legs.size(); i++) {
            double length = legs.get(i);
            if (target <= traversed + length || i == legs.size() - 1) {
                double fraction = length <= 0 ? 0 : (target - traversed) / length;
                Coordinate a = geometry.get(i), b = geometry.get(i + 1);
                return new Coordinate(a.latitude() + (b.latitude() - a.latitude()) * fraction,
                        a.longitude() + (b.longitude() - a.longitude()) * fraction);
            }
            traversed += length;
        }
        return geometry.getLast();
    }

    public static double haversine(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
