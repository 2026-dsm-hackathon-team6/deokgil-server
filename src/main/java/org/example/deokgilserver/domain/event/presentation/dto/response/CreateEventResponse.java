package org.example.deokgilserver.domain.event.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateEventResponse(UUID eventId, String title, LocalDateTime startAt, LocalDateTime endAt, String message) {
}
