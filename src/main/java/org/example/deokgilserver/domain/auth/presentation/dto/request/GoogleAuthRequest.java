package org.example.deokgilserver.domain.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record GoogleAuthRequest(
        @NotBlank String authorizationCode,
        String nickname,
        String profileImage
) {
}
