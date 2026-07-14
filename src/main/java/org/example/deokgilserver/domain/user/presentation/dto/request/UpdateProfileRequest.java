package org.example.deokgilserver.domain.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(@NotBlank String nickname, String profileImage) {
}
