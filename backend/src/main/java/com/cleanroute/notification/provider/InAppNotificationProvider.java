package com.cleanroute.notification.provider;

import com.cleanroute.notification.domain.NotificationModels.NotificationDraft;
import com.cleanroute.notification.repository.NotificationRepository;
import org.springframework.stereotype.Component;
import java.util.UUID;

@Component
public class InAppNotificationProvider implements NotificationProvider {
    private final NotificationRepository repository;
    public InAppNotificationProvider(NotificationRepository repository) { this.repository = repository; }
    @Override public void deliver(UUID userId, NotificationDraft notification) { repository.insert(userId, notification); }
}
