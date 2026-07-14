package org.example.deokgilserver.domain.route.presentation.dto.response;

import java.util.List;
import java.util.UUID;

public record RouteRecommendResponse(UUID eventId, List<RouteStopResponse> route, int totalDistance, int totalTime) {
}
