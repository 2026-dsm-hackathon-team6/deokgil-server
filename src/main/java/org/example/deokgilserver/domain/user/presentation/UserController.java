package org.example.deokgilserver.domain.user.presentation;

import jakarta.validation.Valid;
import org.example.deokgilserver.common.dto.MessageResponse;
import org.example.deokgilserver.domain.user.service.UserService;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateProfileRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PatchMapping
    public UserResponse updateProfile(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }

    @DeleteMapping
    public MessageResponse withdraw(@AuthenticationPrincipal UUID userId) {
        userService.withdraw(userId);
        return new MessageResponse("회원탈퇴가 완료되었습니다.");
    }
}
