package org.example.deokgilserver.domain.schedule.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.schedule.domain.Schedule;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.GenerateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.UpdateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.GenerateScheduleResponse;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.ScheduleResponse;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.UpdateScheduleResponse;
import org.example.deokgilserver.domain.schedule.repository.ScheduleRepository;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ScheduleServiceImpl implements ScheduleService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleExtractionClient scheduleExtractionClient;

    public ScheduleServiceImpl(
            EventRepository eventRepository,
            UserRepository userRepository,
            ScheduleRepository scheduleRepository,
            ScheduleExtractionClient scheduleExtractionClient
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduleExtractionClient = scheduleExtractionClient;
    }

    @Override
    @Transactional
    public GenerateScheduleResponse generate(UUID userId, UUID eventId, GenerateScheduleRequest request) {
        getActiveUser(userId);
        Event event = getOwnedEvent(userId, eventId);

        if (event.getStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
        }
        if (scheduleRepository.existsByEventIdAndStatus(eventId, ScheduleStatus.ACTIVE)) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        List<GeneratedSchedule> generated = scheduleExtractionClient.generate(event, request);

        List<Schedule> saved = scheduleRepository.saveAll(
                generated.stream()
                        .map(item -> Schedule.builder()
                                .event(event)
                                .type(item.type())
                                .title(item.title())
                                .startAt(item.startAt())
                                .endAt(item.endAt())
                                .isAi(true)
                                .build())
                        .toList()
        );

        List<ScheduleResponse> schedules = saved.stream()
                .sorted(java.util.Comparator.comparing(Schedule::getStartAt))
                .map(ScheduleResponse::from)
                .toList();

        return new GenerateScheduleResponse(eventId, schedules);
    }

    @Override
    @Transactional
    public UpdateScheduleResponse update(UUID userId, UpdateScheduleRequest request) {
        getActiveUser(userId);

        if (request.schedules() == null || request.schedules().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_SCHEDULE_LIST);
        }

        List<Schedule> updated = request.schedules().stream()
                .map(item -> applyUpdate(userId, item))
                .toList();

        Set<UUID> affectedEventIds = new LinkedHashSet<>();
        for (Schedule schedule : updated) {
            affectedEventIds.add(schedule.getEvent().getId());
        }
        for (UUID eventId : affectedEventIds) {
            validateNoOverlap(eventId);
        }

        List<ScheduleResponse> schedules = updated.stream()
                .map(ScheduleResponse::from)
                .toList();

        return new UpdateScheduleResponse("일정이 수정되었습니다.", schedules);
    }

    private Schedule applyUpdate(UUID userId, UpdateScheduleRequest.ScheduleUpdateItem item) {
        Schedule schedule = getOwnedSchedule(userId, item.scheduleId());
        if (schedule.getStatus() == ScheduleStatus.DELETED) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }
        Event event = schedule.getEvent();

        if (event.getStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
        }

        LocalDateTime newStartAt = item.startAt() != null ? item.startAt() : schedule.getStartAt();
        LocalDateTime newEndAt = item.endAt() != null ? item.endAt() : schedule.getEndAt();

        if (newEndAt != null && !newEndAt.isAfter(newStartAt)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }
        if (newStartAt.isBefore(event.getStartAt()) || newStartAt.isAfter(event.getEndAt())
                || (newEndAt != null && newEndAt.isAfter(event.getEndAt()))) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE);
        }

        schedule.update(item.title(), item.startAt(), item.endAt());
        return schedule;
    }

    // 같은 행사 내 활성 일정끼리 시간이 겹치는지 확인한다. update()로 인한 변경은 같은 영속성
    // 컨텍스트 안에서 자동 flush되므로, 여기서 다시 조회하면 방금 반영한 값이 그대로 보인다.
    //
    // 시작 시간 순으로만 정렬해서 "바로 앞 항목"과만 비교하면 비인접 구간과의 겹침을 놓친다
    // (예: A 00:00~02:00, B 00:10~00:20, C 00:50~01:00 은 시작 순으로 A,B,C인데 C를 B의
    // 종료(00:20)와만 비교하면 실제로 겹치는 A~C 구간을 놓치게 된다). 그래서 지금까지 본 일정
    // 중 가장 늦게 끝나는 시각(maxEndSoFar)을 계속 갱신하며 비교하는 스윕라인 방식을 쓴다.
    // endAt이 없는 일정은 시작 시각과 동일한 순간의 일(zero-duration)로 취급한다.
    private void validateNoOverlap(UUID eventId) {
        List<Schedule> schedules = scheduleRepository
                .findByEventIdAndStatusOrderByStartAtAsc(eventId, ScheduleStatus.ACTIVE);

        LocalDateTime maxEndSoFar = null;
        for (Schedule schedule : schedules) {
            if (maxEndSoFar != null && schedule.getStartAt().isBefore(maxEndSoFar)) {
                throw new BusinessException(ErrorCode.SCHEDULE_OVERLAP);
            }

            LocalDateTime end = schedule.getEndAt() != null ? schedule.getEndAt() : schedule.getStartAt();
            if (maxEndSoFar == null || end.isAfter(maxEndSoFar)) {
                maxEndSoFar = end;
            }
        }
    }

    @Override
    @Transactional
    public void delete(UUID userId, UUID scheduleId) {
        getActiveUser(userId);
        Schedule schedule = getOwnedSchedule(userId, scheduleId);

        if (schedule.getStatus() == ScheduleStatus.DELETED) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_DELETED);
        }
        if (schedule.getEvent().getStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
        }

        schedule.delete();
    }

    // IDOR 방지: scheduleId만으로 조회하지 않고, 그 일정이 속한 행사의 소유자가 요청자(userId)와
    // 같은지 검증한다(EventServiceImpl.getOwnedEvent와 동일한 패턴). 이게 없으면 로그인한
    // 사용자가 scheduleId를 추측/열거해서 다른 사용자의 일정을 수정·삭제할 수 있다.
    //
    // 삭제 여부(ScheduleStatus.DELETED)는 호출부에서 상황에 맞게 판단한다 — update()는 이미
    // 삭제된 일정을 SCHEDULE_NOT_FOUND로, delete()는 SCHEDULE_ALREADY_DELETED로 구분해서 응답해야 하기 때문이다.
    private Schedule getOwnedSchedule(UUID userId, UUID scheduleId) {
        Schedule schedule = scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));

        if (!schedule.getEvent().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SCHEDULE_ACCESS_DENIED);
        }
        return schedule;
    }

    private void getActiveUser(UUID userId) {
        userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Event getOwnedEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (!event.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }
        return event;
    }
}
