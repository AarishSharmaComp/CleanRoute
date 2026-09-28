package com.cleanroute.notification.api;

import com.cleanroute.notification.domain.NotificationModels.UserNotification;
import com.cleanroute.notification.service.NotificationService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationService notifications;
    public NotificationController(NotificationService notifications) { this.notifications = notifications; }

    @GetMapping
    public List<UserNotification> list(Authentication authentication,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return notifications.list(userId(authentication), limit);
    }

    @PostMapping("/{id}/read")
    public UserNotification markRead(Authentication authentication, @PathVariable UUID id) {
        return notifications.markRead(id, userId(authentication));
    }

    private static UUID userId(Authentication authentication) { return (UUID) authentication.getPrincipal(); }
}
