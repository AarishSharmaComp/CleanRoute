package com.cleanroute.notification.service;

import com.cleanroute.domain.UserPreference;
import com.cleanroute.notification.config.NotificationProperties;
import com.cleanroute.notification.domain.NotificationModels.NotificationDraft;
import com.cleanroute.notification.domain.NotificationModels.UserNotification;
import com.cleanroute.notification.provider.NotificationProvider;
import com.cleanroute.notification.repository.NotificationRepository;
import com.cleanroute.pollution.domain.ForecastModels.PollutionForecast;
import com.cleanroute.repository.UserPreferenceRepository;
import com.cleanroute.route.domain.RoutePlanningModels.CalculationResult;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {
    private final NotificationProvider provider;
    private final NotificationRepository repository;
    private final UserPreferenceRepository preferences;
    private final NotificationProperties properties;

    public NotificationService(NotificationProvider provider, NotificationRepository repository,
                               UserPreferenceRepository preferences, NotificationProperties properties) {
        this.provider = provider; this.repository = repository; this.preferences = preferences; this.properties = properties;
    }

    public List<UserNotification> list(UUID userId, int limit) {
        if (limit < 1 || limit > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Notification limit must be between 1 and 100");
        return repository.list(userId, limit);
    }

    public int unreadCount(UUID userId) { return repository.unreadCount(userId); }

    public UserNotification markRead(UUID id, UUID userId) {
        if (!repository.markRead(id, userId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
        return repository.find(id, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));
    }

    public void checkForecast(UUID userId, PollutionForecast forecast) {
        if (!enabled(userId) || forecast == null) return;
        int sensitivity = preferences.findById(userId).map(UserPreference::getPollutionSensitivity).orElse(3);
        int aqiThreshold = Math.max(1, properties.getHighAqiThreshold() - (sensitivity - 3) * 10);
        double pmThreshold = Math.max(0.1, properties.getHighPm25Threshold() - (sensitivity - 3) * 5);
        boolean highAqi = forecast.aqi() != null && forecast.aqi() >= aqiThreshold;
        boolean highPm = forecast.pm25() != null && forecast.pm25() >= pmThreshold;
        if (!highAqi && !highPm) return;
        provider.deliver(userId, new NotificationDraft("HIGH_POLLUTION_FORECAST",
                "high-forecast:" + forecast.cellId() + ":" + forecast.targetAt(),
                "Elevated pollution forecast",
                "A generated pollution forecast for " + forecast.cellId() + " is above your configured alert threshold."));
    }

    public void checkCleanerAlternative(UUID userId, CalculationResult result) {
        if (!enabled(userId) || result == null || result.alternatives().isEmpty()) return;
        double selectedExposure = result.alternatives().getFirst().expectedPollutionExposure();
        double cleanestExposure = result.alternatives().stream()
                .mapToDouble(a -> a.expectedPollutionExposure()).min().orElse(selectedExposure);
        if (selectedExposure - cleanestExposure < properties.getCleanerExposureImprovement()) return;
        provider.deliver(userId, new NotificationDraft("CLEANER_ALTERNATIVE",
                "cleaner-alternative:" + result.id(), "A cleaner alternative was available",
                "This calculation includes an alternative with at least "
                        + properties.getCleanerExposureImprovement() + " points lower modeled pollution exposure."));
    }

    private boolean enabled(UUID userId) {
        return preferences.findById(userId).map(UserPreference::isNotificationsEnabled).orElse(true);
    }
}
