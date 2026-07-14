package org.example.deokgilserver.domain.user.service;

import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateFcmTokenRequest;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateProfileRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;

import java.util.UUID;

public interface UserService {

    UserResponse updateProfile(UUID userId, UpdateProfileRequest request);

    void withdraw(UUID userId);

    void updateFcmToken(UUID userId, UpdateFcmTokenRequest request);
}
