package org.example.deokgilserver.domain.schedule.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;
import org.example.deokgilserver.domain.schedule.domain.enums.TransportationType;

import java.util.List;

public record GenerateScheduleRequest(
        @NotBlank String purpose,
        List<ScheduleType> priorities,
        @NotNull TransportationType transportation
) {
}
