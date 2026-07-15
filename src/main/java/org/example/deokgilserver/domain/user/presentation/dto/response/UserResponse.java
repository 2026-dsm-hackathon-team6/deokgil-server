package org.example.deokgilserver.domain.user.presentation.dto.response;

import org.example.deokgilserver.domain.user.domain.User;

import java.util.UUID;

public record UserResponse(UUID id, String email, String nickname, String profileImage) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getProfileImage());
    }
}
