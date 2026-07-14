package org.example.deokgilserver.domain.event.presentation.dto.response;

import java.time.LocalDateTime;

public record ExtractEventResponse(
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String placeName,
        String address,
        String eventUrl
) {
}
