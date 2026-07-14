package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.domain.event.presentation.dto.response.EventMapResponse;

import java.util.UUID;

public interface EventMapService {

    EventMapResponse getEventMap(UUID userId, UUID eventId);
}
