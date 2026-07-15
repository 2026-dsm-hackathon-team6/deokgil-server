package org.example.deokgilserver.domain.event.presentation.dto.response;

import org.example.deokgilserver.domain.event.domain.Event;

import java.time.LocalDate;
import java.util.UUID;

public record EventHistoryItemResponse(UUID eventId, String title, LocalDate date, String placeName) {

    public static EventHistoryItemResponse from(Event event) {
        return new EventHistoryItemResponse(
                event.getId(), event.getTitle(), event.getStartAt().toLocalDate(), event.getPlaceName());
    }
}
