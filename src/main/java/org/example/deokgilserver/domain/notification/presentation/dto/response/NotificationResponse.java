package org.example.deokgilserver.domain.notification.presentation.dto.response;

import org.example.deokgilserver.domain.notification.domain.Notification;
import org.example.deokgilserver.domain.notification.domain.enums.NotificationType;
import org.example.deokgilserver.domain.notification.service.NotificationMessageFactory;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID notificationId,
        UUID eventId,
        String eventTitle,
        NotificationType type,
        String title,
        String content,
        LocalDateTime notifyAt,
        boolean sent
) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getEvent().getId(),
                notification.getEvent().getTitle(),
                notification.getType(),
                NotificationMessageFactory.titleFor(notification),
                NotificationMessageFactory.bodyFor(notification),
                notification.getNotifyAt(),
                notification.getSentAt() != null
        );
    }
}
