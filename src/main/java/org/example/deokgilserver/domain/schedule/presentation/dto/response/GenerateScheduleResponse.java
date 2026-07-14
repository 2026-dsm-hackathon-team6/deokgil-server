package org.example.deokgilserver.domain.schedule.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record GenerateScheduleResponse(UUID eventId, List<ScheduleResponse> schedules) {
}
