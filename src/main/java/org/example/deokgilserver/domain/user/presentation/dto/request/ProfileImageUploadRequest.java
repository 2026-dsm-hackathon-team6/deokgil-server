package org.example.deokgilserver.domain.user.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProfileImageUploadRequest(@NotBlank String contentType) {
}
