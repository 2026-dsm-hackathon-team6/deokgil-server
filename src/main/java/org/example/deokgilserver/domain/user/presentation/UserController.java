package org.example.deokgilserver.domain.user.presentation;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.deokgilserver.common.dto.MessageResponse;
import org.example.deokgilserver.common.security.RefreshTokenCookieProvider;
import org.example.deokgilserver.domain.user.service.UserService;
import org.example.deokgilserver.domain.user.presentation.dto.request.ProfileImageUploadRequest;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateFcmTokenRequest;
import org.example.deokgilserver.domain.user.presentation.dto.request.UpdateProfileRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.PresignedProfileImageUploadResponse;
import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    public UserController(UserService userService, RefreshTokenCookieProvider refreshTokenCookieProvider) {
        this.userService = userService;
        this.refreshTokenCookieProvider = refreshTokenCookieProvider;
    }

    @PatchMapping
    public UserResponse updateProfile(@AuthenticationPrincipal UUID userId,
                                       @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(userId, request);
    }

    @DeleteMapping
    public MessageResponse withdraw(@AuthenticationPrincipal UUID userId, HttpServletResponse response) {
        userService.withdraw(userId);
        // 로그아웃과 동일하게 브라우저에 남은 refresh_token 쿠키를 만료시킨다 - 이미 Redis/DB에서
        // 지워진 값이라 재사용해도 실패하지만, 무의미한 쿠키를 클라이언트에 남기지 않기 위함이다.
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString());
        return new MessageResponse("회원탈퇴가 완료되었습니다.");
    }

    // 프로필 이미지를 클라이언트가 S3에 직접 업로드할 수 있도록 presigned URL을 발급한다.
    // 실제 저장(User.profileImage 갱신)은 업로드 완료 후 응답의 imageUrl을 담아 PATCH(위
    // updateProfile)를 별도로 호출해야 반영된다 - 이 API는 URL 발급만 담당한다.
    @PostMapping("/profile-image/presigned-url")
    public PresignedProfileImageUploadResponse createProfileImageUploadUrl(
            @AuthenticationPrincipal UUID userId,
            @Valid @RequestBody ProfileImageUploadRequest request) {
        return userService.createProfileImageUploadUrl(userId, request.contentType());
    }

    // 클라이언트(웹 푸시 구독/FCM SDK)가 발급받은 디바이스 토큰을 등록한다.
    // 로그인 직후, 그리고 토큰이 갱신될 때마다(FCM 토큰은 주기적으로 회전될 수 있음) 호출해야 한다.
    @PatchMapping("/fcm-token")
    public MessageResponse updateFcmToken(@AuthenticationPrincipal UUID userId,
                                           @Valid @RequestBody UpdateFcmTokenRequest request) {
        userService.updateFcmToken(userId, request);
        return new MessageResponse("알림 토큰이 등록되었습니다.");
    }
}
