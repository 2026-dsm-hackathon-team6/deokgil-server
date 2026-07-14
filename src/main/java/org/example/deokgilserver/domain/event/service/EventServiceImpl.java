package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.presentation.dto.request.CreateEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.request.ExtractEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.response.CreateEventResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventDetailResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventHistoryItemResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventHistoryResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventListResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventSummaryResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
import org.example.deokgilserver.domain.notification.service.NotificationService;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.ScheduleResponse;
import org.example.deokgilserver.domain.schedule.repository.ScheduleRepository;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ScheduleRepository scheduleRepository;
    private final NotificationRepository notificationRepository;
    private final ChecklistRepository checklistRepository;
    private final EventExtractionClient eventExtractionClient;
    private final NotificationService notificationService;

    public EventServiceImpl(
            EventRepository eventRepository,
            UserRepository userRepository,
            ScheduleRepository scheduleRepository,
            NotificationRepository notificationRepository,
            ChecklistRepository checklistRepository,
            EventExtractionClient eventExtractionClient,
            NotificationService notificationService
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.checklistRepository = checklistRepository;
        this.eventExtractionClient = eventExtractionClient;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public CreateEventResponse createEvent(UUID userId, CreateEventRequest request) {
        User user = getActiveUser(userId);

        Event event = eventRepository.save(Event.builder()
                .user(user)
                .title(request.title())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .placeName(request.placeName())
                .address(request.address())
                .eventUrl(request.eventUrl())
                .createdType(EventCreatedType.MANUAL)
                .status(EventStatus.ACTIVE)
                .build());

        notificationService.scheduleEventNotifications(event);

        return new CreateEventResponse(event.getId(), event.getTitle(), event.getStartAt(), event.getEndAt(),
                "행사가 등록되었습니다.");
    }

    @Override
    public ExtractEventResponse extractEvent(ExtractEventRequest request) {
        return eventExtractionClient.extract(request.eventUrl());
    }

    @Override
    @Transactional
    public void deleteEvent(UUID userId, UUID eventId) {
        getActiveUser(userId);
        Event event = getOwnedEvent(userId, eventId, ErrorCode.EVENT_NOT_FOUND);

        if (event.getStatus() == EventStatus.DELETED) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_DELETED);
        }

        event.delete();
        // Schedule은 자체 Soft Delete(status/deletedAt)를 갖고 있어 개별 일정 삭제와 동일하게
        // 맞춘다. Notification/Checklist는 별도 이력 개념이 없는 하드 삭제 대상이라 그대로 둔다.
        scheduleRepository.softDeleteByEventId(eventId, LocalDateTime.now());
        notificationRepository.deleteByEventId(eventId);
        checklistRepository.deleteByEventId(eventId);
    }

    @Override
    public EventDetailResponse getEvent(UUID userId, UUID eventId) {
        getActiveUser(userId);
        Event event = eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (!event.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }

        List<ScheduleResponse> schedules = scheduleRepository
                .findByEventIdAndStatusOrderByStartAtAsc(eventId, ScheduleStatus.ACTIVE)
                .stream()
                .map(ScheduleResponse::from)
                .toList();

        if (schedules.isEmpty()) {
            throw new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND);
        }

        return new EventDetailResponse(
                event.getId(),
                event.getTitle(),
                event.getStartAt(),
                event.getEndAt(),
                event.getPlaceName(),
                event.getAddress(),
                event.getLatitude(),
                event.getLongitude(),
                event.getEventUrl(),
                event.getCreatedType(),
                schedules
        );
    }

    @Override
    public EventListResponse getUpcomingEvents(UUID userId, Pageable pageable) {
        Page<Event> events = eventRepository.findByUserIdAndStatusAndEndAtAfterOrderByStartAtAsc(
                userId, EventStatus.ACTIVE, LocalDateTime.now(), pageable);

        List<EventSummaryResponse> summaries = events.stream()
                .map(EventSummaryResponse::from)
                .toList();

        return new EventListResponse(summaries);
    }

    @Override
    public EventHistoryResponse getEventHistory(UUID userId) {
        getActiveUser(userId);

        List<Event> pastEvents = eventRepository.findByUserIdAndStatusAndEndAtBeforeOrderByStartAtDesc(
                userId, EventStatus.ACTIVE, LocalDateTime.now());

        if (pastEvents.isEmpty()) {
            throw new BusinessException(ErrorCode.EVENT_NOT_FOUND);
        }

        List<EventHistoryItemResponse> history = pastEvents.stream()
                .map(EventHistoryItemResponse::from)
                .toList();

        return new EventHistoryResponse(history);
    }

    private User getActiveUser(UUID userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /**
     * IDOR(Insecure Direct Object Reference) 방지: eventId만으로 바로 조회/조작하지 않고,
     * 반드시 요청자(JWT에서 나온 userId)가 그 행사의 소유자인지 함께 검증한다. 이 검증이
     * 없으면 로그인한 사용자가 eventId(UUID)를 추측하거나 다른 API 응답에서 알아내
     * 다른 사람의 행사를 조회/삭제할 수 있다. 이 패턴은 Checklist/Schedule/Route/Briefing
     * 서비스에도 동일하게 반복된다 — 소유권 검증 없이 리소스 ID만 받는 서비스 메서드를
     * 추가하지 않도록 주의할 것.
     */
    private Event getOwnedEvent(UUID userId, UUID eventId, ErrorCode notFoundCode) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(notFoundCode));

        if (!event.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }
        return event;
    }
}
