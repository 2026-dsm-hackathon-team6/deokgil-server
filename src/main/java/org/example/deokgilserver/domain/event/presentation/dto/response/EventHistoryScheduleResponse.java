package org.example.deokgilserver.domain.event.presentation.dto.response;

import org.example.deokgilserver.domain.schedule.domain.Schedule;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventHistoryScheduleResponse(
        UUID scheduleId,
        ScheduleType type,
        String title,
        String description,
        LocalDateTime startAt,
        LocalDateTime endAt
) {

    public static EventHistoryScheduleResponse from(Schedule schedule) {
        return new EventHistoryScheduleResponse(
                schedule.getId(),
                schedule.getType(),
                schedule.getTitle(),
                schedule.getDescription(),
                schedule.getStartAt(),
                schedule.getEndAt()
        );
    }
}
