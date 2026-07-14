package org.example.deokgilserver.domain.auth.presentation.dto.response;

import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;

public record AuthResponse(String accessToken, UserResponse user) {
}
