package org.example.deokgilserver.domain.event.presentation.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull LocalDateTime startAt,
        @NotNull LocalDateTime endAt,
        @Size(max = 200) String placeName,
        @Size(max = 300) String address,
        @Size(max = 2048) @Pattern(regexp = "^https://.*", message = "eventUrl은 https URL이어야 합니다.")
        String eventUrl
) {
    @AssertTrue(message = "endAt은 startAt 이후여야 합니다.")
    public boolean isEndAtAfterStartAt() {
        return startAt == null || endAt == null || !endAt.isBefore(startAt);
    }
}
