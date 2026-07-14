package org.example.deokgilserver.domain.route.presentation.dto.response;

import java.time.LocalDateTime;

public record RouteStopResponse(int order, String placeName, LocalDateTime arrivalTime, int duration) {
}
