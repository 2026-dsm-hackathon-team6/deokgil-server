package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.domain.event.presentation.dto.response.BriefingResponse;

import java.util.UUID;

public interface BriefingService {

    BriefingResponse getBriefing(UUID userId, UUID eventId);
}
