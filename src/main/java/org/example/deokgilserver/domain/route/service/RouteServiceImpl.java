package org.example.deokgilserver.domain.route.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.location.GeoMath;
import org.example.deokgilserver.common.location.PlaceResult;
import org.example.deokgilserver.common.location.PlaceSearchClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.event.service.EventLocationResolver;
import org.example.deokgilserver.domain.route.presentation.dto.request.RouteRecommendRequest;
import org.example.deokgilserver.domain.route.presentation.dto.response.RouteRecommendResponse;
import org.example.deokgilserver.domain.route.presentation.dto.response.RouteStopResponse;
import org.example.deokgilserver.domain.schedule.domain.enums.TransportationType;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 실제 도로/보행 경로가 아닌, 직선거리(haversine) + 이동수단별 평균 속도로 추정한 동선이다.
 * 카카오 모빌리티 길찾기 같은 정식 경로 탐색 API 없이도 "대략 얼마나 걸리는지" 감을 주는
 * MVP 수준의 근사치이며, 요청에 담긴 장소 순서를 그대로 방문 순서로 사용한다(순서 최적화는 하지 않음).
 */
@Service
@Transactional(readOnly = true)
public class RouteServiceImpl implements RouteService {

    private static final int SEARCH_RADIUS_METERS = 5_000;
    private static final int VENUE_ENTRANCE_STAY_MINUTES = 10;
    private static final int DEFAULT_STAY_MINUTES = 20;
    private static final int GOODS_STAY_MINUTES = 30;
    private static final int PERFORMANCE_STAY_MINUTES = 120;

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventLocationResolver eventLocationResolver;
    private final PlaceSearchClient placeSearchClient;

    public RouteServiceImpl(
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
    public RouteRecommendResponse recommend(UUID userId, RouteRecommendRequest request) {
        userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Event event = getOwnedEvent(userId, request.eventId());

        if (event.getStartAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.EVENT_ALREADY_STARTED);
        }

        Coordinate venueCoordinate = eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED);

        try {
            List<StopCandidate> candidates = buildCandidates(event, venueCoordinate, request);
            return computeRoute(event.getId(), event.getStartAt(), candidates, request.transportation());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.ROUTE_GENERATION_FAILED);
        }
    }

    private List<StopCandidate> buildCandidates(Event event, Coordinate venueCoordinate, RouteRecommendRequest request) {
        List<StopCandidate> candidates = new ArrayList<>();
        String venueName = event.getPlaceName() != null ? event.getPlaceName() : event.getTitle();
        candidates.add(new StopCandidate(venueName, venueCoordinate, VENUE_ENTRANCE_STAY_MINUTES));

        if (request.locations() != null) {
            for (RouteRecommendRequest.LocationRequest location : request.locations()) {
                PlaceResult place = placeSearchClient.search(location.placeName(), venueCoordinate, SEARCH_RADIUS_METERS);
                candidates.add(new StopCandidate(place.placeName(), place.coordinate(), stayMinutesFor(location.type())));
            }
        }
        return candidates;
    }

    private int stayMinutesFor(String type) {
        if (type == null) {
            return DEFAULT_STAY_MINUTES;
        }
        String upper = type.toUpperCase();
        if (upper.contains("GOODS")) {
            return GOODS_STAY_MINUTES;
        }
        if (upper.contains("PERFORMANCE")) {
            return PERFORMANCE_STAY_MINUTES;
        }
        return DEFAULT_STAY_MINUTES;
    }

    private RouteRecommendResponse computeRoute(
            UUID eventId, LocalDateTime startAnchor, List<StopCandidate> candidates, TransportationType transportation
    ) {
        double speedMetersPerMinute = speedFor(transportation);

        List<RouteStopResponse> route = new ArrayList<>();
        LocalDateTime currentTime = startAnchor;
        Coordinate previousCoordinate = null;
        int totalDistance = 0;
        int totalTime = 0;

        for (int i = 0; i < candidates.size(); i++) {
            StopCandidate candidate = candidates.get(i);

            if (previousCoordinate != null) {
                double distanceMeters = GeoMath.haversineMeters(previousCoordinate, candidate.coordinate());
                int travelMinutes = (int) Math.ceil(distanceMeters / speedMetersPerMinute);
                currentTime = currentTime.plusMinutes(travelMinutes);
                totalDistance += Math.round(distanceMeters);
                totalTime += travelMinutes;
            }

            route.add(new RouteStopResponse(i + 1, candidate.placeName(), currentTime, candidate.stayMinutes()));
            currentTime = currentTime.plusMinutes(candidate.stayMinutes());
            previousCoordinate = candidate.coordinate();
        }

        return new RouteRecommendResponse(eventId, route, totalDistance, totalTime);
    }

    private double speedFor(TransportationType transportation) {
        return switch (transportation) {
            case WALK -> 67.0;              // 약 4km/h
            case PUBLIC_TRANSPORT -> 250.0; // 약 15km/h (대기/환승 포함 실효 속도)
            case CAR -> 500.0;              // 약 30km/h (도심 주행)
        };
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

    private record StopCandidate(String placeName, Coordinate coordinate, int stayMinutes) {
    }
}
