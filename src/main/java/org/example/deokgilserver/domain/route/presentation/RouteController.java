package org.example.deokgilserver.domain.route.presentation;

import jakarta.validation.Valid;
import org.example.deokgilserver.domain.route.presentation.dto.request.RouteRecommendRequest;
import org.example.deokgilserver.domain.route.presentation.dto.response.RouteRecommendResponse;
import org.example.deokgilserver.domain.route.service.RouteService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/routes")
public class RouteController {

    private final RouteService routeService;

    public RouteController(RouteService routeService) {
        this.routeService = routeService;
    }

    @PostMapping("/recommend")
    public RouteRecommendResponse recommend(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody RouteRecommendRequest request
    ) {
        return routeService.recommend(userId, request);
    }
}
