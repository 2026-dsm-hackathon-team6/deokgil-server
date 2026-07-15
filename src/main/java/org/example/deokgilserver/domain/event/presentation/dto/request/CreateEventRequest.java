package org.example.deokgilserver.domain.event.presentation.dto.request;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.deokgilserver.common.json.EndOfDayLocalDateTimeDeserializer;
import org.example.deokgilserver.common.json.StartOfDayLocalDateTimeDeserializer;

import java.time.LocalDateTime;

public record CreateEventRequest(
        @NotBlank @Size(max = 200) String title,
        @NotNull @JsonDeserialize(using = StartOfDayLocalDateTimeDeserializer.class) LocalDateTime startAt,
        @NotNull @JsonDeserialize(using = EndOfDayLocalDateTimeDeserializer.class) LocalDateTime endAt,
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
