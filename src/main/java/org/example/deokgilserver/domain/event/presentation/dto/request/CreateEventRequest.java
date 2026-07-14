package org.example.deokgilserver.domain.event.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateEventRequest(
        @NotBlank String title,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        String placeName,
        String address,
        String eventUrl
) {
}
