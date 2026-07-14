package org.example.deokgilserver.domain.auth.presentation.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleAuthRequest(
        @NotBlank @Size(max = 2048) String authorizationCode,
        @Size(max = 30) String nickname,
        @Size(max = 2048) @Pattern(regexp = "^https://.*", message = "profileImage는 https URL이어야 합니다.")
        String profileImage
) {
}
