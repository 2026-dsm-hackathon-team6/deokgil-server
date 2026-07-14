package org.example.deokgilserver.domain.schedule.presentation;

import jakarta.validation.Valid;
import org.example.deokgilserver.common.dto.MessageResponse;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.GenerateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.UpdateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.GenerateScheduleResponse;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.UpdateScheduleResponse;
import org.example.deokgilserver.domain.schedule.service.ScheduleService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    @PostMapping("/api/v1/events/{eventId}/schedules/generate")
    public GenerateScheduleResponse generateSchedules(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID eventId,
            @Valid @RequestBody GenerateScheduleRequest request
    ) {
        return scheduleService.generate(userId, eventId, request);
    }

    @PatchMapping("/api/v1/schedules")
    public UpdateScheduleResponse updateSchedules(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody UpdateScheduleRequest request
    ) {
        return scheduleService.update(userId, request);
    }

    @DeleteMapping("/api/v1/schedules/{scheduleId}")
    public MessageResponse deleteSchedule(@AuthenticationPrincipal UUID userId, @PathVariable UUID scheduleId) {
        scheduleService.delete(userId, scheduleId);
        return new MessageResponse("일정이 삭제되었습니다.");
    }
}
