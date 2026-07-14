package org.example.deokgilserver.domain.route.service;

import org.example.deokgilserver.domain.route.presentation.dto.request.RouteRecommendRequest;
import org.example.deokgilserver.domain.route.presentation.dto.response.RouteRecommendResponse;

import java.util.UUID;

public interface RouteService {

    RouteRecommendResponse recommend(UUID userId, RouteRecommendRequest request);
}
