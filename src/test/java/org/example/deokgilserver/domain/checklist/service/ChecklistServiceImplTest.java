package org.example.deokgilserver.domain.checklist.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.weather.WeatherClient;
import org.example.deokgilserver.common.weather.WeatherCondition;
import org.example.deokgilserver.domain.checklist.presentation.dto.response.ChecklistResponse;
import org.example.deokgilserver.domain.checklist.repository.ChecklistRepository;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.example.deokgilserver.domain.event.service.EventLocationResolver;
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
class ChecklistServiceImplTest {

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

    private ChecklistServiceImpl checklistService;

    private final UUID userId = UUID.randomUUID();
    private final UUID eventId = UUID.randomUUID();

    private ChecklistServiceImpl newService() {
        return new ChecklistServiceImpl(
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

    private Event event(UUID id, User owner) {
        Event event = Event.builder()
                .user(owner).title("콘서트").startAt(LocalDateTime.now().plusDays(1))
                .endAt(LocalDateTime.now().plusDays(1).plusHours(3))
                .address("서울 송파구 올림픽로 25")
                .createdType(EventCreatedType.MANUAL).status(EventStatus.ACTIVE).build();
        ReflectionTestUtils.setField(event, "id", id);
        return event;
    }

    @Test
    void 정상적으로_체크리스트를_생성한다() {
        checklistService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(coordinate);
        when(weatherClient.getForecast(coordinate, event.getStartAt())).thenReturn(WeatherCondition.RAIN);
        when(checklistExtractionClient.generateItems(event.getTitle(), WeatherCondition.RAIN))
                .thenReturn(List.of("우산", "여벌 양말"));
        when(checklistRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        ChecklistResponse response = checklistService.generateChecklist(userId, eventId);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.weather()).isEqualTo("비");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).content()).isEqualTo("우산");
        assertThat(response.items().get(0).checked()).isFalse();
        verify(checklistRepository).deleteByEventId(eventId);
    }

    @Test
    void 재생성_시_기존_체크리스트를_삭제하고_새로_저장한다() {
        checklistService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.1));

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(coordinate);
        when(weatherClient.getForecast(any(), any())).thenReturn(WeatherCondition.CLEAR);
        when(checklistExtractionClient.generateItems(anyString(), any())).thenReturn(List.of("선크림"));
        when(checklistRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        checklistService.generateChecklist(userId, eventId);

        var inOrder = inOrder(checklistRepository);
        inOrder.verify(checklistRepository).deleteByEventId(eventId);
        inOrder.verify(checklistRepository).saveAll(anyList());
    }

    @Test
    void 존재하지_않는_행사면_예외가_발생한다() {
        checklistService = newService();
        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> checklistService.generateChecklist(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_NOT_FOUND);

        verifyNoInteractions(eventLocationResolver, weatherClient, checklistExtractionClient);
    }

    @Test
    void 다른_사용자의_행사면_예외가_발생한다() {
        checklistService = newService();
        User owner = activeUser(UUID.randomUUID());
        Event event = event(eventId, owner);

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(activeUser(userId)));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));

        assertThatThrownBy(() -> checklistService.generateChecklist(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_ACCESS_DENIED);
    }

    @Test
    void 위치_확보에_실패하면_예외가_전파되고_날씨_조회를_시도하지_않는다() {
        checklistService = newService();
        User user = activeUser(userId);
        Event event = event(eventId, user);

        when(userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(eventRepository.findByIdAndStatus(eventId, EventStatus.ACTIVE)).thenReturn(Optional.of(event));
        when(eventLocationResolver.resolve(event, ErrorCode.EVENT_LOCATION_REQUIRED)).thenThrow(new BusinessException(ErrorCode.EVENT_LOCATION_REQUIRED));

        assertThatThrownBy(() -> checklistService.generateChecklist(userId, eventId))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EVENT_LOCATION_REQUIRED);

        verifyNoInteractions(weatherClient, checklistExtractionClient);
    }

    @Test
    void 내일_시작하고_체크리스트가_이미_있는_행사만_재생성한다() {
        checklistService = newService();
        User user = activeUser(userId);
        Event withChecklist = event(UUID.randomUUID(), user);
        Event withoutChecklist = event(UUID.randomUUID(), user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.1));

        when(eventRepository.findByStatusAndStartAtBetween(eq(EventStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(withChecklist, withoutChecklist));
        when(checklistRepository.findByEventId(withChecklist.getId()))
                .thenReturn(List.of(org.example.deokgilserver.domain.checklist.domain.Checklist.builder()
                        .event(withChecklist).content("기존 항목").build()));
        when(checklistRepository.findByEventId(withoutChecklist.getId())).thenReturn(List.of());
        when(eventLocationResolver.resolve(withChecklist, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(coordinate);
        when(weatherClient.getForecast(eq(coordinate), any())).thenReturn(WeatherCondition.CLOUDY);
        when(checklistExtractionClient.generateItems(anyString(), any())).thenReturn(List.of("우비"));
        when(checklistRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        checklistService.regenerateChecklistsForTomorrowEvents();

        verify(checklistRepository).deleteByEventId(withChecklist.getId());
        verify(eventLocationResolver, never()).resolve(eq(withoutChecklist), any());
    }

    @Test
    void 내일_시작하는_행사가_없으면_아무것도_하지_않는다() {
        checklistService = newService();
        when(eventRepository.findByStatusAndStartAtBetween(eq(EventStatus.ACTIVE), any(), any()))
                .thenReturn(List.of());

        checklistService.regenerateChecklistsForTomorrowEvents();

        verifyNoInteractions(checklistRepository, eventLocationResolver, weatherClient, checklistExtractionClient);
    }

    @Test
    void 재생성_중_한_건이_실패해도_나머지_행사는_계속_처리한다() {
        checklistService = newService();
        User user = activeUser(userId);
        Event failing = event(UUID.randomUUID(), user);
        Event succeeding = event(UUID.randomUUID(), user);
        Coordinate coordinate = new Coordinate(BigDecimal.valueOf(37.5), BigDecimal.valueOf(127.1));

        when(eventRepository.findByStatusAndStartAtBetween(eq(EventStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(failing, succeeding));
        when(checklistRepository.findByEventId(failing.getId()))
                .thenReturn(List.of(org.example.deokgilserver.domain.checklist.domain.Checklist.builder()
                        .event(failing).content("기존 항목").build()));
        when(checklistRepository.findByEventId(succeeding.getId()))
                .thenReturn(List.of(org.example.deokgilserver.domain.checklist.domain.Checklist.builder()
                        .event(succeeding).content("기존 항목").build()));
        when(eventLocationResolver.resolve(failing, ErrorCode.EVENT_LOCATION_REQUIRED))
                .thenThrow(new BusinessException(ErrorCode.EVENT_LOCATION_REQUIRED));
        when(eventLocationResolver.resolve(succeeding, ErrorCode.EVENT_LOCATION_REQUIRED)).thenReturn(coordinate);
        when(weatherClient.getForecast(eq(coordinate), any())).thenReturn(WeatherCondition.CLEAR);
        when(checklistExtractionClient.generateItems(anyString(), any())).thenReturn(List.of("선크림"));
        when(checklistRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        checklistService.regenerateChecklistsForTomorrowEvents();

        verify(checklistRepository).deleteByEventId(succeeding.getId());
        verify(checklistRepository, never()).deleteByEventId(failing.getId());
    }
}
