package org.example.deokgilserver.domain.event.presentation.dto.response;

import org.example.deokgilserver.domain.event.domain.Event;

import java.time.LocalDate;

public record EventHistoryItemResponse(String title, LocalDate date, String placeName) {

    public static EventHistoryItemResponse from(Event event) {
        return new EventHistoryItemResponse(event.getTitle(), event.getStartAt().toLocalDate(), event.getPlaceName());
    }
}
