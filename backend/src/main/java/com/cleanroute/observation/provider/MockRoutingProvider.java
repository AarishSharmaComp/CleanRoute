package com.cleanroute.observation.provider;

import com.cleanroute.observation.domain.RoutingModels.Coordinate;
import com.cleanroute.observation.domain.RoutingModels.RoutePath;
import com.cleanroute.observation.domain.RoutingModels.RoutingRequest;
import com.cleanroute.domain.TravelMode;
import org.springframework.stereotype.Component;
import java.util.List;

/** Deterministic straight-line demo path; it does not choose or rank routes. */
@Component
public class MockRoutingProvider implements RoutingProvider {
    @Override public String providerId() { return "mock-demo-routing"; }
    @Override public RoutePath route(RoutingRequest request) {
        Coordinate origin = request.origin();
        Coordinate destination = request.destination();
        double distance = pathDistance(origin, destination);
        return new RoutePath(List.of(origin, destination), distance, (int) Math.ceil(distance / speed(request.mode())), providerId(), true, "direct");
    }
    @Override public List<RoutePath> alternatives(RoutingRequest request) {
        Coordinate o = request.origin(), d = request.destination();
        double direct = haversineMeters(o, d);
        double midLat = (o.latitude() + d.latitude()) / 2.0;
        double midLon = (o.longitude() + d.longitude()) / 2.0;
        double latScale = Math.max(0.2, Math.cos(Math.toRadians(midLat)));
        double dx = d.longitude() - o.longitude(), dy = d.latitude() - o.latitude();
        Coordinate northBend = new Coordinate(clamp(midLat + Math.signum(dx == 0 ? 1 : dx) * 0.035, -90, 90),
                clamp(midLon - Math.signum(dy == 0 ? 1 : dy) * 0.035 / latScale, -180, 180));
        Coordinate southBend = new Coordinate(clamp(midLat - Math.signum(dx == 0 ? 1 : dx) * 0.025, -90, 90),
                clamp(midLon + Math.signum(dy == 0 ? 1 : dy) * 0.025 / latScale, -180, 180));
        double northDistance = pathDistance(o, northBend, d);
        double southDistance = pathDistance(o, southBend, d);
        return List.of(
                new RoutePath(List.of(o, d), direct, (int)Math.max(1, Math.ceil(direct / speed(request.mode()))), providerId(), true, "fast-direct"),
                new RoutePath(List.of(o, northBend, d), northDistance, (int)Math.max(1, Math.ceil(northDistance / (speed(request.mode()) * 0.8))), providerId(), true, "detour-north"),
                new RoutePath(List.of(o, southBend, d), southDistance, (int)Math.max(1, Math.ceil(southDistance / (speed(request.mode()) * 0.95))), providerId(), true, "detour-south"));
    }
    private static double speed(TravelMode mode) {
        if (mode == null) return 11.0;
        return switch (mode) { case CAR -> 11.0; case WALK -> 1.4; case CYCLE -> 4.5; case JOG -> 2.8; };
    }
    private static double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }
    private static double pathDistance(Coordinate... points) {
        double distance = 0;
        for (int i = 1; i < points.length; i++) distance += haversineMeters(points[i - 1], points[i]);
        return distance;
    }
    private static double haversineMeters(Coordinate a, Coordinate b) {
        double lat1 = Math.toRadians(a.latitude()), lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1, dLon = Math.toRadians(b.longitude() - a.longitude());
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6_371_000 * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }
}
