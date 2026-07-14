package org.example.deokgilserver.domain.event.presentation.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record BriefingResponse(LocalDateTime departureTime, String weather, List<String> preparation, String transportInfo) {
}
