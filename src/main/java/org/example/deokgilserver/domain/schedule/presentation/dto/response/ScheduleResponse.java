package org.example.deokgilserver.domain.schedule.presentation.dto.response;

import org.example.deokgilserver.domain.schedule.domain.Schedule;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;

import java.time.LocalDateTime;
import java.util.UUID;

public record ScheduleResponse(UUID scheduleId, ScheduleType type, String title, LocalDateTime startAt, LocalDateTime endAt) {

    public static ScheduleResponse from(Schedule schedule) {
        return new ScheduleResponse(
                schedule.getId(),
                schedule.getType(),
                schedule.getTitle(),
                schedule.getStartAt(),
                schedule.getEndAt()
        );
    }
}
