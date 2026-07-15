package org.example.deokgilserver.domain.event.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventHistoryDetailResponse(
        UUID eventId,
        String title,
        String placeName,
        String address,
        LocalDateTime startAt,
        LocalDateTime endAt,
        boolean completed,
        List<EventHistoryScheduleResponse> schedules
) {
}
