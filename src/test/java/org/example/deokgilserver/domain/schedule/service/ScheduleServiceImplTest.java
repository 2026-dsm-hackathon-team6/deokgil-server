package org.example.deokgilserver.domain.schedule.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.schedule.domain.Schedule;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;
import org.example.deokgilserver.domain.schedule.domain.enums.TransportationType;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.GenerateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.UpdateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.GenerateScheduleResponse;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.UpdateScheduleResponse;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ScheduleRepository scheduleRepository;
    @Mock
    private ScheduleExtractionClient scheduleExtractionClient;

    private ScheduleServiceImpl scheduleService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private ScheduleServiceImpl newService() {
        return new ScheduleServiceImpl(eventRepository, userRepository, scheduleRepository, scheduleExtractionClient);
    }

    private User activeUser(UUID id) {
        User user = User.builder()
                .googleId("google-id").email("test@example.com").nickname("tester")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Event event(UUID id, User owner, LocalDateTime startAt, LocalDateTime endAt) {
        Event event = Event.builder()
                .user(owner).title("행사").startAt(startAt).endAt(endAt)
                .createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private Schedule schedule(UUID id, Event event, ScheduleType type, LocalDateTime startAt, LocalDateTime endAt) {
        Schedule schedule = Schedule.builder()
                .event(event).type(type).title("일정").startAt(startAt).endAt(endAt).build();
        ReflectionTestUtils.setField(schedule, "id", id);
        return schedule;
    }

    // ===== generate =====

    @Test
    void 정상적으로_AI_일정을_생성한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        LocalDateTime end = start.plusHours(3);
        Event event = event(eventId, user, start, end);

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(scheduleRepository.existsByEventIdAndStatus(eventId, ScheduleStatus.ACTIVE)).thenReturn(false);

        GenerateScheduleRequest request = new GenerateScheduleRequest(
                "콘서트 관람", List.of(ScheduleType.PERFORMANCE), TransportationType.WALK);
        List<GeneratedSchedule> generated = List.of(
                new GeneratedSchedule(ScheduleType.MOVE, "이동", start, start.plusMinutes(30)),
                new GeneratedSchedule(ScheduleType.PERFORMANCE, "공연", start.plusMinutes(30), end)
        );
        when(scheduleExtractionClient.generate(event, request)).thenReturn(generated);
        when(scheduleRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        GenerateScheduleResponse response = scheduleService.generate(userId, eventId, request);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.schedules()).hasSize(2);
        assertThat(response.schedules().get(0).title()).isEqualTo("이동");
        assertThat(response.schedules().get(1).title()).isEqualTo("공연");
    }

    @Test
    void 이미_시작된_행사에는_일정을_생성할_수_없다() {
        scheduleService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        GenerateScheduleRequest request = new GenerateScheduleRequest("목적", null, TransportationType.CAR);

        assertThatThrownBy(() -> scheduleService.generate(userId, eventId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ALREADY_STARTED);

        verifyNoInteractions(scheduleExtractionClient);
    }

    @Test
    void 이미_생성된_일정이_있으면_재생성할_수_없다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(2));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(scheduleRepository.existsByEventIdAndStatus(eventId, ScheduleStatus.ACTIVE)).thenReturn(true);

        GenerateScheduleRequest request = new GenerateScheduleRequest("목적", null, TransportationType.CAR);

        assertThatThrownBy(() -> scheduleService.generate(userId, eventId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);

        verifyNoInteractions(scheduleExtractionClient);
    }

    @Test
    void 다른_사용자의_행사에는_일정을_생성할_수_없다() {
        scheduleService = newService();
        User requester = activeUser(userId);
        User owner = activeUser(UUID.randomUUID());
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, owner, start, start.plusHours(2));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(requester));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        GenerateScheduleRequest request = new GenerateScheduleRequest("목적", null, TransportationType.CAR);

        assertThatThrownBy(() -> scheduleService.generate(userId, eventId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ACCESS_DENIED);
    }

    // ===== update =====

    @Test
    void 일정을_부분_수정하면_변경된_필드만_반영된다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(5));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(2));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));
        when(scheduleRepository.findByEventIdAndStatusOrderByStartAtAsc(eventId, ScheduleStatus.ACTIVE))
                .thenReturn(List.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(scheduleId, "새 제목", null, null)
        ));

        UpdateScheduleResponse response = scheduleService.update(userId, request);

        assertThat(response.schedules()).hasSize(1);
        assertThat(response.schedules().get(0).title()).isEqualTo("새 제목");
        assertThat(schedule.getStartAt()).isEqualTo(start.plusHours(1));
    }

    @Test
    void 수정할_일정_목록이_비어있으면_예외가_발생한다() {
        scheduleService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of());

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_SCHEDULE_LIST);
    }

    @Test
    void 존재하지_않는_일정을_수정하면_예외가_발생한다() {
        scheduleService = newService();
        UUID scheduleId = UUID.randomUUID();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.empty());

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(scheduleId, "제목", null, null)
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_일정을_수정하면_예외가_발생한다() {
        scheduleService = newService();
        User owner = activeUser(UUID.randomUUID());
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, owner, start, start.plusHours(5));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(2));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(scheduleId, "제목", null, null)
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ACCESS_DENIED);
    }

    @Test
    void 이미_삭제된_일정을_수정하면_예외가_발생한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(5));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(2));
        schedule.delete();

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(scheduleId, "제목", null, null)
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_NOT_FOUND);
    }

    @Test
    void 행사가_이미_시작된_경우_일정을_수정할_수_없다() {
        scheduleService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(2));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS,
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now().plusMinutes(50));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(scheduleId, "제목", null, null)
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ALREADY_STARTED);
    }

    @Test
    void 종료_시간이_시작_시간보다_빠르면_예외가_발생한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(5));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(2));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(
                        scheduleId, null, start.plusHours(2), start.plusHours(1))
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void 행사_시간_범위를_벗어나면_예외가_발생한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(5));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(2));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(
                        scheduleId, null, start.minusHours(1), start.plusHours(1))
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    void 다른_일정과_시간이_겹치면_예외가_발생한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(5));

        UUID movedId = UUID.randomUUID();
        UUID fixedId = UUID.randomUUID();
        Schedule moved = schedule(movedId, event, ScheduleType.GOODS, start.plusHours(3), start.plusHours(4));
        Schedule fixed = schedule(fixedId, event, ScheduleType.PERFORMANCE, start.plusHours(1), start.plusHours(2).plusMinutes(30));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(movedId)).thenReturn(Optional.of(moved));
        // moved를 fixed와 겹치도록 수정 요청 -> 겹침 검사 시점에 최신 상태(moved가 이미 변경된 상태)로 재조회된다고 가정
        when(scheduleRepository.findByEventIdAndStatusOrderByStartAtAsc(eventId, ScheduleStatus.ACTIVE))
                .thenAnswer(inv -> List.of(fixed, moved).stream()
                        .sorted(java.util.Comparator.comparing(Schedule::getStartAt))
                        .toList());

        UpdateScheduleRequest request = new UpdateScheduleRequest(List.of(
                new UpdateScheduleRequest.ScheduleUpdateItem(
                        movedId, null, start.plusHours(2), start.plusHours(3))
        ));

        assertThatThrownBy(() -> scheduleService.update(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_OVERLAP);
    }

    // ===== delete =====

    @Test
    void 일정을_정상적으로_삭제한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(2));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(1).plusMinutes(30));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        scheduleService.delete(userId, scheduleId);

        assertThat(schedule.getStatus()).isEqualTo(ScheduleStatus.DELETED);
    }

    @Test
    void 이미_삭제된_일정을_다시_삭제하면_예외가_발생한다() {
        scheduleService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start, start.plusHours(2));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(1).plusMinutes(30));
        schedule.delete();

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.delete(userId, scheduleId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_DELETED);
    }

    @Test
    void 행사가_이미_시작된_경우_일정을_삭제할_수_없다() {
        scheduleService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user, LocalDateTime.now().minusHours(1), LocalDateTime.now().plusHours(2));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS,
                LocalDateTime.now().plusMinutes(30), LocalDateTime.now().plusMinutes(50));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.delete(userId, scheduleId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ALREADY_STARTED);
    }

    @Test
    void 다른_사용자의_일정을_삭제하면_예외가_발생한다() {
        scheduleService = newService();
        User owner = activeUser(UUID.randomUUID());
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, owner, start, start.plusHours(2));
        UUID scheduleId = UUID.randomUUID();
        Schedule schedule = schedule(scheduleId, event, ScheduleType.GOODS, start.plusHours(1), start.plusHours(1).plusMinutes(30));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(scheduleRepository.findById(scheduleId)).thenReturn(Optional.of(schedule));

        assertThatThrownBy(() -> scheduleService.delete(userId, scheduleId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SCHEDULE_ACCESS_DENIED);
    }
}
