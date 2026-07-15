package org.example.deokgilserver.domain.user.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.storage.S3PresignedUploadService;
import org.example.deokgilserver.domain.auth.repository.RefreshTokenRepository;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.eventrecord.domain.EventRecord;
import org.example.deokgilserver.domain.eventrecord.repository.EventRecordRepository;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private EventRepository eventRepository;
    @Mock
    private EventRecordRepository eventRecordRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ChecklistRepository checklistRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private S3PresignedUploadService s3PresignedUploadService;

    private UserServiceImpl userService;

    private final UUID userId = UUID.randomUUID();

    private UserServiceImpl newService() {
        return new UserServiceImpl(
                userRepository, eventRepository, eventRecordRepository,
                notificationRepository, scheduleRepository, checklistRepository, refreshTokenRepository,
                s3PresignedUploadService);
    }

    private User activeUser() {
        User user = User.builder()
                .googleId("google-id")
                .email("test@example.com")
                .nickname("tester")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(user, "id", userId);
        return user;
    }

    private Event eventWithId(UUID eventId) {
        Event event = Event.builder().build();
        ReflectionTestUtils.setField(event, "id", eventId);
        return event;
    }

    @Test
    void 탈퇴하면_사용자와_소유한_모든_행사_관련_데이터가_물리_삭제된다() {
        userService = newService();
        User user = activeUser();
        UUID eventId1 = UUID.randomUUID();
        UUID eventId2 = UUID.randomUUID();
        List<Event> events = List.of(eventWithId(eventId1), eventWithId(eventId2));
        List<EventRecord> eventRecords = List.of();

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByUserId(userId)).thenReturn(events);
        when(eventRecordRepository.findByUserId(userId)).thenReturn(eventRecords);

        userService.withdraw(userId);

        verify(notificationRepository).deleteByEventId(eventId1);
        verify(notificationRepository).deleteByEventId(eventId2);
        verify(scheduleRepository).deleteByEventId(eventId1);
        verify(scheduleRepository).deleteByEventId(eventId2);
        verify(checklistRepository).deleteByEventId(eventId1);
        verify(checklistRepository).deleteByEventId(eventId2);
        verify(eventRecordRepository).deleteAll(eventRecords);
        verify(eventRepository).deleteAll(events);
        verify(refreshTokenRepository).delete(userId);
        verify(userRepository).delete(user);
    }

    @Test
    void 존재하지_않는_사용자가_탈퇴를_시도하면_USER_NOT_FOUND_예외가_발생하고_아무것도_삭제되지_않는다() {
        userService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.withdraw(userId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verifyNoInteractions(eventRepository, eventRecordRepository, notificationRepository,
                scheduleRepository, checklistRepository, refreshTokenRepository);
        verify(userRepository, never()).delete(any());
    }
}
