package org.example.deokgilserver.domain.event.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExtractEventRequest(
        @NotBlank @Size(max = 2048) @Pattern(regexp = "^https://.*", message = "eventUrl은 https URL이어야 합니다.")
        String eventUrl
) {
}
