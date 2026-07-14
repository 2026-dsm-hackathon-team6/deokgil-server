package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.domain.event.presentation.dto.request.CreateEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.request.ExtractEventRequest;
import org.example.deokgilserver.domain.event.presentation.dto.response.CreateEventResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventDetailResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.EventListResponse;
import org.example.deokgilserver.domain.event.presentation.dto.response.ExtractEventResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface EventService {

    CreateEventResponse createEvent(UUID userId, CreateEventRequest request);

    ExtractEventResponse extractEvent(ExtractEventRequest request);

    void deleteEvent(UUID userId, UUID eventId);

    EventDetailResponse getEvent(UUID userId, UUID eventId);

    EventListResponse getUpcomingEvents(UUID userId, Pageable pageable);
}
