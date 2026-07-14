package org.example.deokgilserver.domain.schedule.presentation.dto.response;

import java.util.List;

public record UpdateScheduleResponse(String message, List<ScheduleResponse> schedules) {
}
