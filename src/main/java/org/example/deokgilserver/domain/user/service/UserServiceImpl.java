package org.example.deokgilserver.domain.user.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateFcmTokenRequest;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateProfileRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;
import org.example.deokgilserver.domain.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;

    public UserServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = getActiveUser(userId);
        user.updateProfile(request.nickname(), request.profileImage());
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public void withdraw(UUID userId) {
        User user = getActiveUser(userId);
        user.withdraw();
    }

    @Override
    @Transactional
    public void updateFcmToken(UUID userId, UpdateFcmTokenRequest request) {
        User user = getActiveUser(userId);
        user.updateFcmToken(request.fcmToken());
    }

    private User getActiveUser(UUID userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }
}
