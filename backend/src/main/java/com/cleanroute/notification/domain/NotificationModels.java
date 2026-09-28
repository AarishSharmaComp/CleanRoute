package com.cleanroute.notification.domain;

import java.time.Instant;
import java.util.UUID;

public final class NotificationModels {
    private NotificationModels() {}
    public record NotificationDraft(String kind, String dedupKey, String title, String message) {}
    public record UserNotification(UUID id, String kind, String title, String message,
                                   Instant createdAt, Instant readAt) {}
}
