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
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional(readOnly = true)
public class ChecklistServiceImpl implements ChecklistService {

    // AI 응답(체크리스트 항목)은 신뢰할 수 없는 외부 입력으로 취급한다 - event.getTitle()이
    // 사용자 입력이거나 외부 URL에서 AI로 추출된 값이라 프롬프트 인젝션 통제가 불가능하다.
    // 개수/길이 상한이 없으면 인젝션으로 수백 개·비정상적으로 긴 항목이 그대로 저장되거나,
    // Checklist.content 컬럼(기본 VARCHAR(255)) 길이를 넘겨 DB 레벨 예외로 이어질 수 있다.
    private static final int MAX_ITEMS = 30;
    private static final int MAX_ITEM_LENGTH = 255;

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

        log.info("AI 준비물 목록 생성 요청: eventId={}", eventId);
        GenerationResult result = generate(event);
        log.info("AI 준비물 목록 생성 완료: eventId={}, count={}", eventId, result.checklists().size());

        return ChecklistResponse.of(eventId, result.weather().label(), result.checklists());
    }

    /**
     * 체크리스트 생성 시점에 행사가 아직 몇 달 뒤라 단기예보 범위 밖이면 KmaWeatherClient가
     * 날씨를 UNKNOWN으로 돌려주고, 그 상태로 생성된 체크리스트는 날씨를 반영하지 못한 채
     * 굳어버린다. 행사 하루 전이면 확실히 단기예보 범위 안에 들어오므로(D+2 이내) 그 시점에
     * 한 번 더 최신 날씨로 재생성해서 정확도를 보정한다. 아직 한 번도 생성한 적 없는 행사까지
     * 자동으로 만들어주는 건 사용자가 요청한 적 없는 동작이라 대상에서 제외한다.
     */
    @Override
    @Scheduled(cron = "0 30 5 * * *")
    @Transactional
    public void regenerateChecklistsForTomorrowEvents() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        LocalDateTime start = tomorrow.atStartOfDay();
        LocalDateTime end = tomorrow.plusDays(1).atStartOfDay();

        List<Event> events = eventRepository.findByStatusAndStartAtBetween(EventStatus.ACTIVE, start, end);
        if (events.isEmpty()) {
            return;
        }

        log.info("행사 하루 전 체크리스트 재생성 대상 {}건", events.size());
        for (Event event : events) {
            if (checklistRepository.findByEventId(event.getId()).isEmpty()) {
                continue;
            }
            try {
                GenerationResult result = generate(event);
                log.info("체크리스트 재생성 완료: eventId={}, count={}", event.getId(), result.checklists().size());
            } catch (Exception e) {
                // 한 건이 실패해도(위치 정보 없음, AI 호출 실패 등) 나머지 행사 처리에 영향을
                // 주지 않는다 — 기존 체크리스트는 deleteByEventId 전에 실패하면 그대로 남는다.
                log.warn("체크리스트 재생성 실패 (eventId={}): {}", event.getId(), e.getMessage());
            }
        }
    }

    private GenerationResult generate(Event event) {
        Coordinate coordinate = eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED);
        WeatherCondition weather = weatherClient.getForecast(coordinate, event.getStartAt());
        List<String> items = checklistExtractionClient.generateItems(event.getTitle(), weather);
        validateItems(items);

        // 재생성 시 이전 추천 결과는 버리고 새 목록으로 교체한다(체크 여부도 함께 초기화됨).
        checklistRepository.deleteByEventId(event.getId());
        List<Checklist> saved = checklistRepository.saveAll(
                items.stream()
                        .map(content -> Checklist.builder().event(event).content(content).build())
                        .toList()
        );

        return new GenerationResult(weather, saved);
    }

    private record GenerationResult(WeatherCondition weather, List<Checklist> checklists) {
    }

    private void validateItems(List<String> items) {
        if (items.size() > MAX_ITEMS) {
            throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
        }
        for (String item : items) {
            if (item == null || item.isBlank() || item.length() > MAX_ITEM_LENGTH) {
                throw new BusinessException(ErrorCode.AI_GENERATION_FAILED);
            }
        }
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
