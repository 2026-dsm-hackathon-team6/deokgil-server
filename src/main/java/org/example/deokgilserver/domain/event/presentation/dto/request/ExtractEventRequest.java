package org.example.deokgilserver.domain.event.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ExtractEventRequest(@NotBlank String eventUrl) {
}
