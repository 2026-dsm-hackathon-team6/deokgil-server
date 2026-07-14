package org.example.deokgilserver.domain.event.presentation.dto.response;

import org.example.deokgilserver.domain.event.domain.Event;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventSummaryResponse(UUID eventId, String title, LocalDateTime startAt, LocalDateTime endAt, String placeName) {

    public static EventSummaryResponse from(Event event) {
        return new EventSummaryResponse(
                event.getId(),
                event.getTitle(),
                event.getStartAt(),
                event.getEndAt(),
                event.getPlaceName()
        );
    }
}
