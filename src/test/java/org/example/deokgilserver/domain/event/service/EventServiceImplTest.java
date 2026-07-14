package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventHistoryResponse;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.event.presentation.dto.request.CreateEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.response.CreateEventResponse;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
import org.example.deokgilserver.domain.notification.service.NotificationService;
import org.example.deokgilserver.domain.schedule.repository.ScheduleRepository;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserRole;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ChecklistRepository checklistRepository;
    @Mock
    private EventExtractionClient eventExtractionClient;
    @Mock
    private NotificationService notificationService;

    private EventServiceImpl eventService;

    private final UUID userId = UUID.randomUUID();

    private EventServiceImpl newService() {
        return new EventServiceImpl(
                eventRepository, userRepository, scheduleRepository,
                notificationRepository, checklistRepository, eventExtractionClient, notificationService);
    }

    private User activeUser(UUID id) {
        User user = User.builder()
                .googleId("google-id").email("test@example.com").nickname("tester")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Event event(User owner, String title, LocalDateTime startAt, String placeName) {
        return Event.builder()
                .user(owner).title(title).startAt(startAt).endAt(startAt.plusHours(2))
                .placeName(placeName).createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
    }

    @Test
    void 행사_생성에_성공하면_알림이_예약된다() {
        eventService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        CreateEventRequest request = new CreateEventRequest(
                "아이브 콘서트", start, start.plusHours(2), "KSPO DOME", "서울 송파구 올림픽로 424", null);

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.save(any(Event.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateEventResponse response = eventService.createEvent(userId, request);

        assertThat(response.title()).isEqualTo("아이브 콘서트");
        verify(notificationService).scheduleEventNotifications(any(Event.class));
    }

    @Test
    void 종료된_행사를_최신순으로_반환한다() {
        eventService = newService();
        User user = activeUser(userId);
        Event past1 = event(user, "아이브 콘서트", LocalDateTime.now().minusDays(10), "KSPO DOME");
        Event past2 = event(user, "뉴진스 콘서트", LocalDateTime.now().minusDays(3), "고척스카이돔");

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByUserIdAndStatusAndEndAtBeforeOrderByStartAtDesc(
                eq(userId), eq(EventStatus.ACTIVE), any()))
                .thenReturn(List.of(past2, past1));

        EventHistoryResponse response = eventService.getEventHistory(userId);

        assertThat(response.events()).hasSize(2);
        assertThat(response.events().get(0).title()).isEqualTo("뉴진스 콘서트");
        assertThat(response.events().get(0).placeName()).isEqualTo("고척스카이돔");
    }

    @Test
    void 종료된_행사가_없으면_예외가_발생한다() {
        eventService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByUserIdAndStatusAndEndAtBeforeOrderByStartAtDesc(any(), any(), any()))
                .thenReturn(List.of());

        assertThatThrownBy(() -> eventService.getEventHistory(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }
}
