package org.example.deokgilserver.domain.schedule.presentation.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// schedules가 비어있는 경우는 ErrorCode.INVALID_SCHEDULE_LIST로 응답해야 하므로,
// bean validation(@NotEmpty)이 아니라 서비스 레이어에서 직접 검사한다.
public record UpdateScheduleRequest(
        @Valid List<ScheduleUpdateItem> schedules
) {

    public record ScheduleUpdateItem(
            @NotNull UUID scheduleId,
            String title,
            LocalDateTime startAt,
            LocalDateTime endAt
    ) {
    }
}
