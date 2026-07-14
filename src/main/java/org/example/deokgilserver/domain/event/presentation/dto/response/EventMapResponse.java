package org.example.deokgilserver.domain.event.presentation.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record EventMapResponse(
        String placeName,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        List<FacilityResponse> facilities
) {
}
