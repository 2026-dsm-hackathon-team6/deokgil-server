package org.example.deokgilserver.domain.checklist.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.weather.WeatherClient;
import org.example.deokgilserver.common.weather.WeatherCondition;
import org.example.deokgilserver.domain.checklist.domain.Checklist;
import org.example.deokgilserver.domain.checklist.presentation.dto.response.ChecklistResponse;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.event.service.EventLocationResolver;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ChecklistServiceImpl implements ChecklistService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ChecklistRepository checklistRepository;
    private final EventLocationResolver eventLocationResolver;
    private final WeatherClient weatherClient;
    private final ChecklistExtractionClient checklistExtractionClient;

    public ChecklistServiceImpl(
            EventRepository eventRepository,
            UserRepository userRepository,
            ChecklistRepository checklistRepository,
            EventLocationResolver eventLocationResolver,
            WeatherClient weatherClient,
            ChecklistExtractionClient checklistExtractionClient
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.checklistRepository = checklistRepository;
        this.eventLocationResolver = eventLocationResolver;
        this.weatherClient = weatherClient;
        this.checklistExtractionClient = checklistExtractionClient;
    }

    @Override
    @Transactional
    public ChecklistResponse generateChecklist(UUID userId, UUID eventId) {
        userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Event event = getOwnedEvent(userId, eventId);

        Coordinate coordinate = eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED);
        WeatherCondition weather = weatherClient.getForecast(coordinate, event.getStartAt());
        List<String> items = checklistExtractionClient.generateItems(event.getTitle(), weather);

        // 재생성 시 이전 추천 결과는 버리고 새 목록으로 교체한다(체크 여부도 함께 초기화됨).
        checklistRepository.deleteByEventId(eventId);
        List<Checklist> saved = checklistRepository.saveAll(
                items.stream()
                        .map(content -> Checklist.builder().event(event).content(content).build())
                        .toList()
        );

        return ChecklistResponse.of(eventId, weather.label(), saved);
    }

    // IDOR 방지: eventId만으로 조회하지 않고 요청자(userId)가 실제 소유자인지 검증한다
    // (EventServiceImpl.getOwnedEvent와 동일한 패턴).
    private Event getOwnedEvent(UUID userId, UUID eventId) {
        Event event = eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.EVENT_NOT_FOUND));

        if (!event.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.EVENT_ACCESS_DENIED);
        }
        return event;
    }
}
