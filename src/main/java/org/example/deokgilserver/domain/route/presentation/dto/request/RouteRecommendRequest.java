package org.example.deokgilserver.domain.route.presentation.dto.request;

import jakarta.validation.constraints.NotNull;
import org.example.deokgilserver.domain.schedule.domain.enums.TransportationType;

import java.util.List;
import java.util.UUID;

public record RouteRecommendRequest(
        @NotNull UUID eventId,
        List<LocationRequest> locations,
        @NotNull TransportationType transportation
) {

    public record LocationRequest(String type, String placeName) {
    }
}
