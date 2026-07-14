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
import org.example.deokgilserver.domain.event.presentation.dto.response.EventListResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventSummaryResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.notification.repository.NotificationRepository;
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

    public EventServiceImpl(
            EventRepository eventRepository,
            UserRepository userRepository,
            ScheduleRepository scheduleRepository,
            NotificationRepository notificationRepository,
            ChecklistRepository checklistRepository,
            EventExtractionClient eventExtractionClient
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
        this.notificationRepository = notificationRepository;
        this.checklistRepository = checklistRepository;
        this.eventExtractionClient = eventExtractionClient;
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
        scheduleRepository.deleteByEventId(eventId);
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

        List<ScheduleResponse> schedules = scheduleRepository.findByEventIdOrderByStartAtAsc(eventId).stream()
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

    private User getActiveUser(UUID userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private Event getOwnedEvent(UUID userId, UUID eventId, ErrorCode notFoundCode) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(notFoundCode));

        if (!event.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }
        return event;
    }
}
