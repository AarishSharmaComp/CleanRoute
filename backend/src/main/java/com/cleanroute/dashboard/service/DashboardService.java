package com.cleanroute.dashboard.service;

import com.cleanroute.dashboard.domain.DashboardModels.*;
import com.cleanroute.domain.RouteHistory;
import com.cleanroute.domain.SavedPlace;
import com.cleanroute.domain.SavedRoute;
import com.cleanroute.notification.domain.NotificationModels.UserNotification;
import com.cleanroute.notification.config.NotificationProperties;
import com.cleanroute.notification.service.NotificationService;
import com.cleanroute.observation.domain.ObservationModels.GeographicCell;
import com.cleanroute.observation.domain.ObservationModels.PollutionObservation;
import com.cleanroute.observation.repository.ObservationRepository;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.pollution.service.PollutionForecastService;
import com.cleanroute.repository.RouteHistoryRepository;
import com.cleanroute.repository.SavedPlaceRepository;
import com.cleanroute.repository.SavedRouteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class DashboardService {
    private static final String DEFAULT_CELL = "demo-delhi-central";
    private final ObservationRepository observations;
    private final PollutionForecastService forecasts;
    private final SavedPlaceRepository places;
    private final SavedRouteRepository savedRoutes;
    private final RouteHistoryRepository routeHistory;
    private final NotificationService notifications;
    private final NotificationProperties notificationProperties;

    public DashboardService(ObservationRepository observations, PollutionForecastService forecasts,
            SavedPlaceRepository places, SavedRouteRepository savedRoutes, RouteHistoryRepository routeHistory,
            NotificationService notifications, NotificationProperties notificationProperties) {
        this.observations = observations; this.forecasts = forecasts; this.places = places;
        this.savedRoutes = savedRoutes; this.routeHistory = routeHistory; this.notifications = notifications;
        this.notificationProperties = notificationProperties;
    }

    public DashboardResponse get(UUID userId) {
        Instant now = Instant.now();
        String cellId = observations.cells().stream().map(GeographicCell::cellId)
                .filter(DEFAULT_CELL::equals).findFirst().orElse(null);
        EnvironmentalPoint current = null;
        List<EnvironmentalPoint> observed = List.of();
        List<EnvironmentalPoint> predicted = List.of();
        if (cellId != null) {
            current = observations.latest(cellId).stream().findFirst().map(DashboardService::observedPoint).orElse(null);
            observed = observations.history(cellId, now.minus(Duration.ofHours(24)), now, 96, 0).stream()
                    .sorted(Comparator.comparing(PollutionObservation::observedAt))
                    .map(DashboardService::observedPoint).toList();
            try {
                List<PollutionForecast> future = forecasts.forecastSeries(cellId, ceilQuarter(now.plusSeconds(1)), 15, 8);
                future.forEach(f -> notifications.checkForecast(userId, f));
                predicted = future.stream().map(DashboardService::forecastPoint).toList();
            } catch (ResponseStatusException unavailable) {
                if (unavailable.getStatusCode() != HttpStatus.UNPROCESSABLE_ENTITY
                        && unavailable.getStatusCode() != HttpStatus.NOT_FOUND) throw unavailable;
            }
        }

        List<SavedPlaceItem> savedPlaceItems = places.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DashboardService::placeItem).toList();
        List<SavedRouteItem> savedRouteItems = savedRoutes.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DashboardService::savedRouteItem).toList();
        List<RouteHistoryItem> routeHistoryItems = routeHistory.findAllByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(DashboardService::routeHistoryItem).toList();
        List<UserNotification> notificationItems = notifications.list(userId, notificationProperties.getResultLimit());
        return new DashboardResponse(cellId, current, observed, predicted, savedPlaceItems, savedRouteItems,
                routeHistoryItems, notificationItems, notifications.unreadCount(userId));
    }

    private static EnvironmentalPoint observedPoint(PollutionObservation o) {
        return new EnvironmentalPoint(o.cellId(), o.observedAt(), o.aqi(), o.pm25(), "OBSERVED", o.generated(), null, null);
    }
    private static EnvironmentalPoint forecastPoint(PollutionForecast f) {
        return new EnvironmentalPoint(f.cellId(), f.targetAt(), f.aqi(), f.pm25(), "PREDICTED", f.sourceGenerated(), f.quality(), f.qualityScore());
    }
    private static SavedPlaceItem placeItem(SavedPlace p) {
        return new SavedPlaceItem(p.getId().toString(), p.getName(), p.getLatitude(), p.getLongitude(), p.getAddress());
    }
    private static SavedRouteItem savedRouteItem(SavedRoute r) {
        return new SavedRouteItem(r.getId().toString(), r.getOriginName(), r.getDestinationName(), r.getTravelMode(), r.getRoutePreference(), r.getPollutionScore());
    }
    private static RouteHistoryItem routeHistoryItem(RouteHistory r) {
        return new RouteHistoryItem(r.getId().toString(), r.getOriginName(), r.getDestinationName(), r.getTravelMode(),
                r.getRoutePreference(), r.getPollutionScore(), r.getEstimatedTravelTimeSeconds(), r.getDistanceMeters(), r.getCreatedAt());
    }
    private static Instant ceilQuarter(Instant time) {
        long floor = Math.floorDiv(time.getEpochSecond(), 900) * 900;
        return Instant.ofEpochSecond(time.getEpochSecond() == floor ? floor : floor + 900);
    }
}
