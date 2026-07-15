package org.example.deokgilserver.domain.event.presentation;

import jakarta.validation.Valid;
import org.example.deokgilserver.common.dto.MessageResponse;
import org.example.deokgilserver.domain.event.presentation.dto.request.CreateEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.request.ExtractEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.response.BriefingResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.CreateEventResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventDetailResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventHistoryDetailResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventHistoryResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventListResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventMapResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
import org.example.deokgilserver.domain.event.service.BriefingService;
import org.example.deokgilserver.domain.event.service.EventMapService;
import org.example.deokgilserver.domain.event.service.EventService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {

    private final EventService eventService;
    private final BriefingService briefingService;
    private final EventMapService eventMapService;

    public EventController(EventService eventService, BriefingService briefingService, EventMapService eventMapService) {
        this.eventService = eventService;
        this.briefingService = briefingService;
        this.eventMapService = eventMapService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreateEventResponse createEvent(@AuthenticationPrincipal UUID userId,
                                            @Valid @RequestBody CreateEventRequest request) {
        return eventService.createEvent(userId, request);
    }

    @PostMapping("/extract")
    public ExtractEventResponse extractEvent(@Valid @RequestBody ExtractEventRequest request) {
        return eventService.extractEvent(request);
    }

    @GetMapping("/list")
    public EventListResponse getUpcomingEvents(@AuthenticationPrincipal UUID userId,
                                                @PageableDefault(size = 10) Pageable pageable) {
        return eventService.getUpcomingEvents(userId, pageable);
    }

    @GetMapping("/history")
    public EventHistoryResponse getEventHistory(@AuthenticationPrincipal UUID userId) {
        return eventService.getEventHistory(userId);
    }

    @GetMapping("/history/{eventId}")
    public EventHistoryDetailResponse getEventHistoryDetail(@AuthenticationPrincipal UUID userId,
                                                              @PathVariable UUID eventId) {
        return eventService.getEventHistoryDetail(userId, eventId);
    }

    @GetMapping("/{eventId}")
    public EventDetailResponse getEvent(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        return eventService.getEvent(userId, eventId);
    }

    @DeleteMapping("/{eventId}")
    public MessageResponse deleteEvent(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        eventService.deleteEvent(userId, eventId);
        return new MessageResponse("이벤트가 삭제되었습니다.");
    }

    @GetMapping("/{eventId}/briefing")
    public BriefingResponse getBriefing(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        return briefingService.getBriefing(userId, eventId);
    }

    @GetMapping("/{eventId}/map")
    public EventMapResponse getEventMap(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        return eventMapService.getEventMap(userId, eventId);
    }
}
