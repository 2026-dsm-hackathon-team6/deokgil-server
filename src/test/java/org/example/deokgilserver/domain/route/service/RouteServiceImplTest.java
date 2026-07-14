package org.example.deokgilserver.domain.route.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.location.GeoMath;
import org.example.deokgilserver.common.location.PlaceResult;
import org.example.deokgilserver.common.location.PlaceSearchClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.event.service.EventLocationResolver;
import org.example.deokgilserver.domain.route.presentation.dto.request.RouteRecommendRequest;
import org.example.deokgilserver.domain.route.presentation.dto.response.RouteRecommendResponse;
import org.example.deokgilserver.domain.schedule.domain.enums.TransportationType;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserRole;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RouteServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EventLocationResolver eventLocationResolver;
    @Mock
    private PlaceSearchClient placeSearchClient;

    private RouteServiceImpl routeService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private RouteServiceImpl newService() {
        return new RouteServiceImpl(eventRepository, userRepository, eventLocationResolver, placeSearchClient);
    }

    private User activeUser(UUID id) {
        User user = User.builder()
                .googleId("google-id").email("test@example.com").nickname("tester")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Event event(UUID id, User owner, LocalDateTime startAt) {
        Event event = Event.builder()
                .user(owner).title("콘서트").startAt(startAt).endAt(startAt.plusHours(3))
                .placeName("KSPO DOME").createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    void 방문할_장소가_없으면_행사장_한_곳만_동선에_포함된다() {
        routeService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start);
        Coordinate venueCoordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(venueCoordinate);

        RouteRecommendRequest request = new RouteRecommendRequest(eventId, null, TransportationType.WALK);

        RouteRecommendResponse response = routeService.recommend(userId, request);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.route()).hasSize(1);
        assertThat(response.route().get(0).order()).isEqualTo(1);
        assertThat(response.route().get(0).placeName()).isEqualTo("KSPO DOME");
        assertThat(response.route().get(0).arrivalTime()).isEqualTo(start);
        assertThat(response.route().get(0).duration()).isEqualTo(10);
        assertThat(response.totalDistance()).isZero();
        assertThat(response.totalTime()).isZero();
        verifyNoInteractions(placeSearchClient);
    }

    @Test
    void 방문_장소가_있으면_이동_거리와_시간이_누적되고_체류_시간이_유형별로_다르게_적용된다() {
        routeService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start);
        Coordinate venueCoordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));
        Coordinate placeCoordinate = new Coordinate(BigDecimal.valueOf(37.51), BigDecimal.valueOf(127.0));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(venueCoordinate);
        when(placeSearchClient.search("굿즈샵", venueCoordinate, 5_000))
                .thenReturn(new PlaceResult("굿즈 판매 부스", placeCoordinate));

        RouteRecommendRequest request = new RouteRecommendRequest(
                eventId,
                List.of(new RouteRecommendRequest.LocationRequest("GOODS", "굿즈샵")),
                TransportationType.WALK
        );

        RouteRecommendResponse response = routeService.recommend(userId, request);

        double distanceMeters = GeoMath.haversineMeters(venueCoordinate, placeCoordinate);
        int expectedTravelMinutes = (int) Math.ceil(distanceMeters / 67.0);

        assertThat(response.route()).hasSize(2);
        assertThat(response.route().get(1).placeName()).isEqualTo("굿즈 판매 부스");
        assertThat(response.route().get(1).duration()).isEqualTo(30);
        assertThat(response.route().get(1).arrivalTime())
                .isEqualTo(start.plusMinutes(10).plusMinutes(expectedTravelMinutes));
        assertThat(response.totalDistance()).isEqualTo((int) Math.round(distanceMeters));
        assertThat(response.totalTime()).isEqualTo(expectedTravelMinutes);
    }

    @Test
    void 이미_시작된_행사는_동선을_추천할_수_없다() {
        routeService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user, LocalDateTime.now().minusHours(1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        RouteRecommendRequest request = new RouteRecommendRequest(eventId, null, TransportationType.WALK);

        assertThatThrownBy(() -> routeService.recommend(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ALREADY_STARTED);

        verifyNoInteractions(eventLocationResolver, placeSearchClient);
    }

    @Test
    void 존재하지_않는_행사면_예외가_발생한다() {
        routeService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.empty());

        RouteRecommendRequest request = new RouteRecommendRequest(eventId, null, TransportationType.WALK);

        assertThatThrownBy(() -> routeService.recommend(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_행사면_예외가_발생한다() {
        routeService = newService();
        User owner = activeUser(UUID.randomUUID());
        Event event = event(eventId, owner, LocalDateTime.now().plusDays(1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        RouteRecommendRequest request = new RouteRecommendRequest(eventId, null, TransportationType.WALK);

        assertThatThrownBy(() -> routeService.recommend(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ACCESS_DENIED);
    }

    @Test
    void 장소_검색에서_발생한_비즈니스_예외는_그대로_전파된다() {
        routeService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start);
        Coordinate venueCoordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(venueCoordinate);
        when(placeSearchClient.search(anyString(), any(), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_LOCATION));

        RouteRecommendRequest request = new RouteRecommendRequest(
                eventId,
                List.of(new RouteRecommendRequest.LocationRequest("GOODS", "존재하지 않는 장소")),
                TransportationType.WALK
        );

        assertThatThrownBy(() -> routeService.recommend(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_LOCATION);
    }

    @Test
    void 예상치_못한_오류는_ROUTE_GENERATION_FAILED로_변환된다() {
        routeService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start);
        Coordinate venueCoordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(venueCoordinate);
        when(placeSearchClient.search(anyString(), any(), anyInt()))
                .thenThrow(new RuntimeException("unexpected"));

        RouteRecommendRequest request = new RouteRecommendRequest(
                eventId,
                List.of(new RouteRecommendRequest.LocationRequest("GOODS", "장소")),
                TransportationType.WALK
        );

        assertThatThrownBy(() -> routeService.recommend(userId, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ROUTE_GENERATION_FAILED);
    }
}
