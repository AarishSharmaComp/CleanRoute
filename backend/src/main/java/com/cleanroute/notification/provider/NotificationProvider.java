package com.cleanroute.notification.provider;

import com.cleanroute.notification.domain.NotificationModels.NotificationDraft;
import java.util.UUID;

/** Delivery boundary for future channels; the MVP implementation persists in-app notifications. */
public interface NotificationProvider {
    void deliver(UUID userId, NotificationDraft notification);
}
