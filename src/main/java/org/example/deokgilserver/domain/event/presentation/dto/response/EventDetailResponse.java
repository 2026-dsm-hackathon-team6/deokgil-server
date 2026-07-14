package org.example.deokgilserver.domain.event.presentation.dto.response;

import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.ScheduleResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record EventDetailResponse(
        UUID eventId,
        String title,
        LocalDateTime startAt,
        LocalDateTime endAt,
        String placeName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        String eventUrl,
        EventCreatedType createdType,
        List<ScheduleResponse> schedules
) {
}
