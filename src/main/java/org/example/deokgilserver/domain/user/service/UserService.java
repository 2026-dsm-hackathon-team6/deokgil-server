package org.example.deokgilserver.domain.user.service;

import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateFcmTokenRequest;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateProfileRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.PresignedProfileImageUploadResponse;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    void withdraw(UUID userId);

    void updateFcmToken(UUID userId, UpdateFcmTokenRequest request);

    PresignedProfileImageUploadResponse createProfileImageUploadUrl(UUID userId, String contentType);

    /**
     * 회원가입 시점에는 아직 계정(userId)이 없어 사용자별 prefix를 쓸 수 없으므로, 공용
     * "pending" prefix 아래에 UUID로만 구분되는 키를 만든다. 인증 없이 호출 가능한 API이므로
     * (AuthController, SecurityConfig permitAll) rate limit으로 남용을 막는다(RateLimitFilter).
     */
    PresignedProfileImageUploadResponse createProfileImageUploadUrlForSignup(String contentType);
}
