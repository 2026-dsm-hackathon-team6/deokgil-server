package org.example.deokgilserver.domain.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.push.PushNotificationClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.notification.domain.Notification;
import org.example.deokgilserver.domain.notification.domain.enums.NotificationType;
import org.example.deokgilserver.domain.notification.presentation.dto.response.NotificationListResponse;
import org.example.deokgilserver.domain.notification.presentation.dto.response.NotificationResponse;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 알림 시각 계산은 전부 행사 시작/종료 시각(event.startAt/endAt) 기준의 단순한 고정 오프셋이다.
 * "출발" 알림의 이상적인 시점은 실제 이동 시간(사용자 출발지 + 교통수단)을 반영해야 하지만,
 * BriefingServiceImpl과 같은 이유로 사용자의 출발 위치 개념이 아직 없어 정확히 계산할 수 없다 —
 * 그래서 event.startAt 기준 1시간 전이라는 보수적인 기본값을 쓴다. 나중에 출발지 입력이
 * 생기면 RouteServiceImpl과 같은 방식(GeoMath + 이동수단별 속도)으로 정교화할 수 있다.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class NotificationServiceImpl implements NotificationService {

    private static final int DEFAULT_DEPARTURE_BUFFER_HOURS = 1;
    private static final int DEFAULT_PERFORMANCE_BUFFER_MINUTES = 10;

    private final NotificationRepository notificationRepository;
    private final PushNotificationClient pushNotificationClient;
    private final UserRepository userRepository;

    public NotificationServiceImpl(
            NotificationRepository notificationRepository,
            PushNotificationClient pushNotificationClient,
            UserRepository userRepository
    ) {
        this.notificationRepository = notificationRepository;
        this.pushNotificationClient = pushNotificationClient;
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public void scheduleEventNotifications(Event event) {
        LocalDateTime now = LocalDateTime.now();
        scheduleIfFuture(event, NotificationType.DAY_BEFORE, event.getStartAt().minusDays(1), now);
        scheduleIfFuture(event, NotificationType.DEPARTURE, event.getStartAt().minusHours(DEFAULT_DEPARTURE_BUFFER_HOURS), now);
        scheduleIfFuture(event, NotificationType.PERFORMANCE_START, event.getStartAt().minusMinutes(DEFAULT_PERFORMANCE_BUFFER_MINUTES), now);
        scheduleIfFuture(event, NotificationType.RETURN, event.getEndAt(), now);
    }

    private void scheduleIfFuture(Event event, NotificationType type, LocalDateTime notifyAt, LocalDateTime now) {
        // 이미 지난 시각이면(예: 행사 시작까지 하루가 안 남은 경우의 DAY_BEFORE) 예약해도
        // 절대 발송될 일이 없으므로 아예 만들지 않는다 — 알림 테이블에 죽은 행이 쌓이지 않도록.
        if (notifyAt.isBefore(now)) {
            return;
        }
        notificationRepository.save(Notification.builder()
                .event(event)
                .type(type)
                .notifyAt(notifyAt)
                .enabled(true)
                .build());
    }

    // 1분마다 발송 대상을 확인한다. 정확히 그 순간에 보내지 못해도 최대 1분 오차이므로
    // 알림 용도로는 충분하다.
    @Override
    @Scheduled(fixedRate = 60_000)
    @Transactional
    public void dispatchDueNotifications() {
        List<Notification> due = notificationRepository.findByNotifyAtBeforeAndSentAtIsNullAndEnabledTrue(LocalDateTime.now());

        for (Notification notification : due) {
            try {
                dispatchOne(notification);
            } catch (Exception e) {
                // 한 건이 실패해도 나머지 알림 발송에 영향을 주지 않는다. sentAt을 기록하지
                // 않았으므로 다음 스케줄 주기에 다시 시도된다.
                log.warn("알림 발송 실패 (notificationId={}): {}", notification.getId(), e.getMessage());
            }
        }
    }

    private void dispatchOne(Notification notification) {
        User user = notification.getEvent().getUser();
        if (!StringUtils.hasText(user.getFcmToken())) {
            // 디바이스 토큰이 아직 등록되지 않은 사용자 - 보낼 방법이 없으니 건너뛴다.
            // sentAt을 남기지 않으므로 토큰이 등록되면 다음 주기에 자동으로 발송 시도된다.
            return;
        }

        pushNotificationClient.send(
                user.getFcmToken(),
                NotificationMessageFactory.titleFor(notification),
                NotificationMessageFactory.bodyFor(notification)
        );
        notification.markSent();
    }

    @Override
    public NotificationListResponse getNotifications(UUID userId) {
        userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<NotificationResponse> notifications = notificationRepository
                .findByEvent_User_IdOrderByNotifyAtDesc(userId).stream()
                .map(NotificationResponse::from)
                .toList();

        return new NotificationListResponse(notifications);
    }
}
