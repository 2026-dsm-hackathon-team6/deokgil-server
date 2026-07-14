package org.example.deokgilserver.domain.notification.service;

import org.example.deokgilserver.common.push.PushNotificationClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.notification.domain.Notification;
import org.example.deokgilserver.domain.notification.domain.enums.NotificationType;
import org.example.deokgilserver.domain.notification.presentation.dto.response.NotificationListResponse;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserRole;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private PushNotificationClient pushNotificationClient;
    @Mock
    private UserRepository userRepository;

    private NotificationServiceImpl notificationService;

    private NotificationServiceImpl newService() {
        return new NotificationServiceImpl(notificationRepository, pushNotificationClient, userRepository);
    }

    private User user(String fcmToken) {
        User user = User.builder()
                .googleId("google-id").email("test@example.com").nickname("tester")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        if (fcmToken != null) {
            user.updateFcmToken(fcmToken);
        }
        return user;
    }

    private Event event(User owner, LocalDateTime startAt) {
        return Event.builder()
                .user(owner).title("콘서트").startAt(startAt).endAt(startAt.plusHours(2))
                .placeName("KSPO DOME").createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
    }

    private Notification notification(UUID id, Event event, NotificationType type, LocalDateTime notifyAt) {
        Notification notification = Notification.builder().event(event).type(type).notifyAt(notifyAt).enabled(true).build();
        ReflectionTestUtils.setField(notification, "id", id);
        return notification;
    }

    // ===== scheduleEventNotifications =====

    @Test
    void 행사_등록시_미래_시각의_알림_4종이_모두_예약된다() {
        notificationService = newService();
        LocalDateTime start = LocalDateTime.now().plusDays(5);
        Event event = event(user("token"), start);

        notificationService.scheduleEventNotifications(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(4)).save(captor.capture());
        List<Notification> saved = captor.getAllValues();

        assertThat(saved).extracting(Notification::getType)
                .containsExactlyInAnyOrder(
                        NotificationType.DAY_BEFORE, NotificationType.DEPARTURE,
                        NotificationType.PERFORMANCE_START, NotificationType.RETURN);
    }

    @Test
    void 이미_지난_시각으로_계산되는_알림은_예약하지_않는다() {
        notificationService = newService();
        // 시작까지 5분밖에 안 남아서 DAY_BEFORE(-1일)/DEPARTURE(-1시간)/PERFORMANCE_START(-10분)가
        // 전부 이미 과거 시각으로 계산된다 - RETURN(행사 종료 시각)만 미래라 예약되어야 한다.
        LocalDateTime start = LocalDateTime.now().plusMinutes(5);
        Event event = event(user("token"), start);

        notificationService.scheduleEventNotifications(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(NotificationType.RETURN);
    }

    // ===== dispatchDueNotifications =====

    @Test
    void 발송_대상_알림을_푸시로_보내고_발송_시각을_기록한다() {
        notificationService = newService();
        User owner = user("device-token");
        Event event = event(owner, LocalDateTime.now().plusDays(1));
        Notification due = notification(UUID.randomUUID(), event, NotificationType.DAY_BEFORE, LocalDateTime.now().minusMinutes(1));

        when(notificationRepository.findByNotifyAtBeforeAndSentAtIsNullAndEnabledTrue(any()))
                .thenReturn(List.of(due));

        notificationService.dispatchDueNotifications();

        verify(pushNotificationClient).send(eq("device-token"), anyString(), anyString());
        assertThat(due.getSentAt()).isNotNull();
    }

    @Test
    void FCM_토큰이_없는_사용자는_건너뛰고_발송_기록을_남기지_않는다() {
        notificationService = newService();
        User owner = user(null);
        Event event = event(owner, LocalDateTime.now().plusDays(1));
        Notification due = notification(UUID.randomUUID(), event, NotificationType.DAY_BEFORE, LocalDateTime.now().minusMinutes(1));

        when(notificationRepository.findByNotifyAtBeforeAndSentAtIsNullAndEnabledTrue(any()))
                .thenReturn(List.of(due));

        notificationService.dispatchDueNotifications();

        verifyNoInteractions(pushNotificationClient);
        assertThat(due.getSentAt()).isNull();
    }

    @Test
    void 한_건의_발송_실패가_다른_알림_발송에_영향을_주지_않는다() {
        notificationService = newService();
        User owner1 = user("token-1");
        User owner2 = user("token-2");
        Event event1 = event(owner1, LocalDateTime.now().plusDays(1));
        Event event2 = event(owner2, LocalDateTime.now().plusDays(1));
        Notification failing = notification(UUID.randomUUID(), event1, NotificationType.DAY_BEFORE, LocalDateTime.now().minusMinutes(1));
        Notification succeeding = notification(UUID.randomUUID(), event2, NotificationType.RETURN, LocalDateTime.now().minusMinutes(1));

        when(notificationRepository.findByNotifyAtBeforeAndSentAtIsNullAndEnabledTrue(any()))
                .thenReturn(List.of(failing, succeeding));
        doThrow(new RuntimeException("전송 실패")).when(pushNotificationClient).send(eq("token-1"), anyString(), anyString());

        notificationService.dispatchDueNotifications();

        assertThat(failing.getSentAt()).isNull();
        assertThat(succeeding.getSentAt()).isNotNull();
    }

    // ===== getNotifications =====

    @Test
    void 사용자의_알림을_최신순으로_조회한다() {
        notificationService = newService();
        UUID userId = UUID.randomUUID();
        User owner = user("token");
        ReflectionTestUtils.setField(owner, "id", userId);
        Event event = event(owner, LocalDateTime.now().plusDays(1));
        Notification latest = notification(UUID.randomUUID(), event, NotificationType.DEPARTURE, LocalDateTime.now().plusHours(23));
        Notification earlier = notification(UUID.randomUUID(), event, NotificationType.DAY_BEFORE, LocalDateTime.now().plusHours(1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(owner));
        when(notificationRepository.findByEvent_User_IdOrderByNotifyAtDesc(userId))
                .thenReturn(List.of(latest, earlier));

        NotificationListResponse response = notificationService.getNotifications(userId);

        assertThat(response.notifications()).hasSize(2);
        assertThat(response.notifications().get(0).type()).isEqualTo(NotificationType.DEPARTURE);
        assertThat(response.notifications().get(0).title()).isEqualTo("출발할 시간이에요");
        assertThat(response.notifications().get(1).type()).isEqualTo(NotificationType.DAY_BEFORE);
    }

    @Test
    void 존재하지_않는_사용자면_예외가_발생한다() {
        notificationService = newService();
        UUID userId = UUID.randomUUID();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> notificationService.getNotifications(userId))
                .isInstanceOf(org.example.deokgilserver.common.exception.BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(org.example.deokgilserver.common.exception.ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(notificationRepository);
    }
}
