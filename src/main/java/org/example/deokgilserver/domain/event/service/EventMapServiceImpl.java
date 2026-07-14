package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.location.GeoMath;
import org.example.deokgilserver.common.location.PlaceResult;
import org.example.deokgilserver.common.location.PlaceSearchClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventMapResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.FacilityResponse;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EventMapServiceImpl implements EventMapService {

    private static final int FACILITY_SEARCH_RADIUS_METERS = 1_000;

    // 참고사항에 명시된 편의시설 4종. Kakao 카테고리 검색(category_group_code)에는
    // 코인락커/화장실 카테고리가 없어, 4종 모두 키워드 검색으로 통일해서 찾는다.
    private static final List<FacilityKeyword> FACILITY_KEYWORDS = List.of(
            new FacilityKeyword("LOCKER", "코인락커"),
            new FacilityKeyword("CONVENIENCE_STORE", "편의점"),
            new FacilityKeyword("CAFE", "카페"),
            new FacilityKeyword("RESTROOM", "화장실")
    );

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventLocationResolver eventLocationResolver;
    private final PlaceSearchClient placeSearchClient;

    public EventMapServiceImpl(
            EventRepository eventRepository,
            UserRepository userRepository,
            EventLocationResolver eventLocationResolver,
            PlaceSearchClient placeSearchClient
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.eventLocationResolver = eventLocationResolver;
        this.placeSearchClient = placeSearchClient;
    }

    @Override
    @Transactional
    public EventMapResponse getEventMap(UUID userId, UUID eventId) {
        userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Event event = getOwnedEvent(userId, eventId);

        Coordinate coordinate = eventLocationResolver.resolve(event, ErrorCode.LOCATION_NOT_FOUND);
        List<FacilityResponse> facilities = findNearbyFacilities(coordinate);

        return new EventMapResponse(
                event.getPlaceName(),
                event.getAddress(),
                coordinate.latitude(),
                coordinate.longitude(),
                facilities
        );
    }

    private List<FacilityResponse> findNearbyFacilities(Coordinate venueCoordinate) {
        List<FacilityResponse> facilities = new ArrayList<>();
        for (FacilityKeyword facility : FACILITY_KEYWORDS) {
            try {
                PlaceResult place = placeSearchClient.search(
                        facility.keyword(), venueCoordinate, FACILITY_SEARCH_RADIUS_METERS);
                int distance = (int) Math.round(GeoMath.haversineMeters(venueCoordinate, place.coordinate()));
                facilities.add(new FacilityResponse(facility.type(), place.placeName(), distance));
            } catch (BusinessException e) {
                if (e.getErrorCode() != ErrorCode.INVALID_LOCATION) {
                    throw e;
                }
                // 반경 내에 해당 유형의 시설이 없는 경우 - 그 항목만 건너뛴다.
            }
        }
        return facilities;
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

    private record FacilityKeyword(String type, String keyword) {
    }
}
