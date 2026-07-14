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
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.presentation.dto.response.BriefingResponse;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BriefingServiceImplTest {

    @Mock
    private EventRepository eventRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ChecklistRepository checklistRepository;
    @Mock
    private EventLocationResolver eventLocationResolver;
    @Mock
    private WeatherClient weatherClient;
    @Mock
    private ChecklistExtractionClient checklistExtractionClient;

    private BriefingServiceImpl briefingService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private BriefingServiceImpl newService() {
        return new BriefingServiceImpl(
                eventRepository, userRepository, checklistRepository,
                eventLocationResolver, weatherClient, checklistExtractionClient);
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
                .createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    private Checklist checklist(Event event, String content) {
        return Checklist.builder().event(event).content(content).build();
    }

    @Test
    void 이미_생성된_체크리스트가_있으면_그대로_준비물로_사용하고_AI를_다시_호출하지_않는다() {
        briefingService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(coordinate);
        when(weatherClient.getForecast(coordinate, start)).thenReturn(WeatherCondition.RAIN);
        when(checklistRepository.findByEventId(eventId))
                .thenReturn(List.of(checklist(event, "우산"), checklist(event, "여벌 양말")));

        BriefingResponse response = briefingService.getBriefing(userId, eventId);

        assertThat(response.weather()).isEqualTo("비");
        assertThat(response.preparation()).containsExactly("우산", "여벌 양말");
        assertThat(response.departureTime()).isNull();
        assertThat(response.transportInfo()).isNull();
        verifyNoInteractions(checklistExtractionClient);
    }

    @Test
    void 체크리스트가_없으면_AI로_임시_생성해서_보여주되_저장하지_않는다() {
        briefingService = newService();
        User user = activeUser(userId);
        LocalDateTime start = LocalDateTime.now().plusDays(1);
        Event event = event(eventId, user, start);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.0));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(coordinate);
        when(weatherClient.getForecast(coordinate, start)).thenReturn(WeatherCondition.CLEAR);
        when(checklistRepository.findByEventId(eventId)).thenReturn(List.of());
        when(checklistExtractionClient.generateItems(event.getTitle(), WeatherCondition.CLEAR))
                .thenReturn(List.of("선크림", "모자"));

        BriefingResponse response = briefingService.getBriefing(userId, eventId);

        assertThat(response.weather()).isEqualTo("맑음");
        assertThat(response.preparation()).containsExactly("선크림", "모자");
        verify(checklistRepository, never()).saveAll(any());
        verify(checklistRepository, never()).save(any());
    }

    @Test
    void 이미_시작된_행사는_브리핑을_조회할_수_없다() {
        briefingService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user, LocalDateTime.now().minusHours(1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> briefingService.getBriefing(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ALREADY_STARTED);

        verifyNoInteractions(eventLocationResolver, weatherClient, checklistExtractionClient);
    }

    @Test
    void 존재하지_않는_행사면_예외가_발생한다() {
        briefingService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefingService.getBriefing(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void 다른_사용자의_행사면_예외가_발생한다() {
        briefingService = newService();
        User owner = activeUser(UUID.randomUUID());
        Event event = event(eventId, owner, LocalDateTime.now().plusDays(1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> briefingService.getBriefing(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ACCESS_DENIED);
    }
}
