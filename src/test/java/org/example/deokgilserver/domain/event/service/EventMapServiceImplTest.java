package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.location.PlaceResult;
import org.example.deokgilserver.common.location.PlaceSearchClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventMapResponse;
import org.example.deokgilserver.domain.event.repository.EventRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventMapServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EventLocationResolver eventLocationResolver;
    @Mock
    private PlaceSearchClient placeSearchClient;

    private EventMapServiceImpl eventMapService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private EventMapServiceImpl newService() {
        return new EventMapServiceImpl(eventRepository, userRepository, eventLocationResolver, placeSearchClient);
    }

    private User activeUser(UUID id) {
        User user = User.builder()
                .googleId("google-id").email("test@example.com").nickname("tester")
                .role(UserRole.USER).status(UserStatus.ACTIVE).build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Event event(UUID id, User owner) {
        Event event = Event.builder()
                .user(owner).title("콘서트")
                .startAt(LocalDateTime.now().plusDays(1)).endAt(LocalDateTime.now().plusDays(1).plusHours(3))
                .placeName("KSPO DOME").address("서울특별시 송파구 올림픽로 424")
                .createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    void 정상적으로_행사장_지도와_주변_시설을_반환한다() {
        eventMapService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5121), BigDecimal.valueOf(127.0982));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.LOCATION_NOT_FOUND)).thenReturn(coordinate);
        when(placeSearchClient.search(eq("코인락커"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("행사장 코인락커", coordinate));
        when(placeSearchClient.search(eq("편의점"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("CU 편의점", coordinate));
        when(placeSearchClient.search(eq("카페"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("스타벅스", coordinate));
        when(placeSearchClient.search(eq("화장실"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("공용 화장실", coordinate));

        EventMapResponse response = eventMapService.getEventMap(userId, eventId);

        assertThat(response.placeName()).isEqualTo("KSPO DOME");
        assertThat(response.address()).isEqualTo("서울특별시 송파구 올림픽로 424");
        assertThat(response.latitude()).isEqualTo(coordinate.latitude());
        assertThat(response.facilities()).hasSize(4);
        assertThat(response.facilities()).extracting("type")
                .containsExactly("LOCKER", "CONVENIENCE_STORE", "CAFE", "RESTROOM");
        assertThat(response.facilities().get(0).distance()).isZero();
    }

    @Test
    void 특정_시설_유형이_주변에_없으면_해당_항목만_빠지고_나머지는_반환된다() {
        eventMapService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5121), BigDecimal.valueOf(127.0982));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.LOCATION_NOT_FOUND)).thenReturn(coordinate);
        when(placeSearchClient.search(eq("코인락커"), eq(coordinate), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.INVALID_LOCATION));
        when(placeSearchClient.search(eq("편의점"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("CU 편의점", coordinate));
        when(placeSearchClient.search(eq("카페"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("스타벅스", coordinate));
        when(placeSearchClient.search(eq("화장실"), eq(coordinate), anyInt()))
                .thenReturn(new PlaceResult("공용 화장실", coordinate));

        EventMapResponse response = eventMapService.getEventMap(userId, eventId);

        assertThat(response.facilities()).hasSize(3);
        assertThat(response.facilities()).extracting("type")
                .containsExactly("CONVENIENCE_STORE", "CAFE", "RESTROOM");
    }

    @Test
    void 지도_API_오류는_그대로_전파된다() {
        eventMapService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5121), BigDecimal.valueOf(127.0982));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.LOCATION_NOT_FOUND)).thenReturn(coordinate);
        when(placeSearchClient.search(eq("코인락커"), eq(coordinate), anyInt()))
                .thenThrow(new BusinessException(ErrorCode.MAP_API_ERROR));

        assertThatThrownBy(() -> eventMapService.getEventMap(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MAP_API_ERROR);
    }

    @Test
    void 위치_정보가_없으면_LOCATION_NOT_FOUND_예외가_발생한다() {
        eventMapService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.LOCATION_NOT_FOUND))
                .thenThrow(new BusinessException(ErrorCode.LOCATION_NOT_FOUND));

        assertThatThrownBy(() -> eventMapService.getEventMap(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.LOCATION_NOT_FOUND);

        verifyNoInteractions(placeSearchClient);
    }

    @Test
    void 존재하지_않는_행사면_예외가_발생한다() {
        eventMapService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> eventMapService.getEventMap(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_행사면_예외가_발생한다() {
        eventMapService = newService();
        User owner = activeUser(UUID.randomUUID());
        Event event = event(eventId, owner);

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> eventMapService.getEventMap(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ACCESS_DENIED);
    }
}
