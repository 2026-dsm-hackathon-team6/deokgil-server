package org.example.deokgilserver.domain.schedule.service;

import org.example.deokgilserver.domain.schedule.presentation.dto.request.GenerateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.UpdateScheduleRequest;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.GenerateScheduleResponse;
import org.example.deokgilserver.domain.schedule.presentation.dto.response.UpdateScheduleResponse;

import java.util.UUID;

public interface ScheduleService {

    GenerateScheduleResponse generate(UUID userId, UUID eventId, GenerateScheduleRequest request);

    UpdateScheduleResponse update(UUID userId, UpdateScheduleRequest request);

    void delete(UUID userId, UUID scheduleId);
}
