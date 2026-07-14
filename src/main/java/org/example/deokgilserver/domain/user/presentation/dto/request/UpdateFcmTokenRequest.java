package org.example.deokgilserver.domain.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateFcmTokenRequest(@NotBlank String fcmToken) {
}
