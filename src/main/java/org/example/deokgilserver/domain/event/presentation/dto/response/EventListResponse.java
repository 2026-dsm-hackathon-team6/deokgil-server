package org.example.deokgilserver.domain.event.presentation.dto.response;

import java.util.List;

public record EventListResponse(List<EventSummaryResponse> events) {
}
