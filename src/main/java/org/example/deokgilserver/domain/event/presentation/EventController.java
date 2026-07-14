package org.example.deokgilserver.domain.event.presentation;

import jakarta.validation.Valid;
import org.example.deokgilserver.common.dto.MessageResponse;
import org.example.deokgilserver.domain.event.presentation.dto.request.CreateEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.request.ExtractEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.response.CreateEventResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventDetailResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventListResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
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

    public EventController(EventService eventService) {
        this.eventService = eventService;
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

    @GetMapping("/{eventId}")
    public EventDetailResponse getEvent(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        return eventService.getEvent(userId, eventId);
    }

    @DeleteMapping("/{eventId}")
    public MessageResponse deleteEvent(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        eventService.deleteEvent(userId, eventId);
        return new MessageResponse("이벤트가 삭제되었습니다.");
    }
}
