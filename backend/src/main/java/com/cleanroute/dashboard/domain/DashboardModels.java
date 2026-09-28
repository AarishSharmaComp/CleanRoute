package com.cleanroute.dashboard.domain;

import com.cleanroute.domain.RoutePreference;
import com.cleanroute.domain.TravelMode;
import com.cleanroute.notification.domain.NotificationModels.UserNotification;
import java.time.Instant;
import java.util.List;

public final class DashboardModels {
    private DashboardModels() {}
    public record EnvironmentalPoint(String cellId, Instant timestamp, Integer aqi, Double pm25,
                                     String dataType, boolean generated, String quality, Integer qualityScore) {}
    public record SavedPlaceItem(String id, String name, double latitude, double longitude, String address) {}
    public record SavedRouteItem(String id, String originName, String destinationName,
                                 TravelMode mode, RoutePreference preference, Double exposure) {}
    public record RouteHistoryItem(String id, String originName, String destinationName,
                                  TravelMode mode, RoutePreference preference, Double pollutionScore,
                                  Integer durationSeconds, Double distanceMeters, Instant createdAt) {}
    public record DashboardResponse(String cellId, EnvironmentalPoint current,
            List<EnvironmentalPoint> observations, List<EnvironmentalPoint> forecasts,
            List<SavedPlaceItem> savedPlaces, List<SavedRouteItem> savedRoutes,
            List<RouteHistoryItem> routeHistory, List<UserNotification> notifications,
            int unreadNotificationCount) {}
}
