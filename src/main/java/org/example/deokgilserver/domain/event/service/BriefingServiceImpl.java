package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.weather.WeatherClient;
import org.example.deokgilserver.common.weather.WeatherCondition;
import org.example.deokgilserver.domain.checklist.domain.Checklist;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.checklist.service.ChecklistExtractionClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.presentation.dto.response.BriefingResponse;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class BriefingServiceImpl implements BriefingService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final ChecklistRepository checklistRepository;
    private final EventLocationResolver eventLocationResolver;
    private final WeatherClient weatherClient;
    private final ChecklistExtractionClient checklistExtractionClient;

    public BriefingServiceImpl(
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

    // 클래스 기본값은 readOnly=true지만, 이 메서드는 EventLocationResolver.resolve()를 통해
    // 좌표를 최초 1회 지오코딩해서 이벤트에 캐싱 저장한다(체크리스트/동선 생성과 동일한 방식).
    // readOnly 트랜잭션에서는 이 저장이 자동 flush되지 않고 조용히 버려지므로, 매 브리핑
    // 조회마다 지오코딩 API를 다시 호출하게 된다 — 그래서 쓰기 가능한 트랜잭션으로 재정의한다.
    @Override
    @Transactional
    public BriefingResponse getBriefing(UUID userId, UUID eventId) {
        userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Event event = getOwnedEvent(userId, eventId);

        // 스펙상 에러명은 EVENT_NOT_STARTED지만, 조건은 다른 엔드포인트의 "이미 시작된 행사"
        // 제약과 동일(행사 시작 전에만 허용)하므로 같은 ErrorCode(EVENT_ALREADY_STARTED)를 재사용한다.
        if (event.getStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
        }

        Coordinate coordinate = eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED);
        WeatherCondition weather = weatherClient.getForecast(coordinate, event.getStartAt());
        List<String> preparation = resolvePreparation(event, weather);

        // departureTime/transportInfo는 사용자의 출발 위치가 있어야 계산할 수 있는데,
        // 현재 User/Event 모델에는 "출발지" 개념이 없어 실제 값을 낼 수 없다. 값을 지어내는
        // 대신 null로 비워둔다 — 출발 위치 입력(예: 사용자 프로필의 기본 주소, 혹은 요청 파라미터)이
        // 추가되면 route 계산과 동일한 방식(GeoMath + 이동수단별 속도)으로 채울 수 있다.
        return new BriefingResponse(null, weather.label(), preparation, null);
    }

    private List<String> resolvePreparation(Event event, WeatherCondition weather) {
        List<Checklist> existing = checklistRepository.findByEventId(event.getId());
        if (!existing.isEmpty()) {
            return existing.stream().map(Checklist::getContent).toList();
        }
        // 아직 체크리스트를 생성한 적 없는 행사면, 조회 시점에 임시로 추천만 해서 보여준다
        // (GET 요청이므로 여기서 DB에 저장하지는 않는다 — 저장은 체크리스트 생성 API의 책임).
        return checklistExtractionClient.generateItems(event.getTitle(), weather);
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
