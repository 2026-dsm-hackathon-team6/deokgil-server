package org.example.deokgilserver.domain.notification.presentation;

import org.example.deokgilserver.domain.notification.presentation.dto.response.NotificationListResponse;
import org.example.deokgilserver.domain.notification.service.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public NotificationListResponse getNotifications(@AuthenticationPrincipal UUID userId) {
        return notificationService.getNotifications(userId);
    }
}
