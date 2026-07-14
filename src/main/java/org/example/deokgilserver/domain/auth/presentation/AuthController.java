package org.example.deokgilserver.domain.auth.presentation;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.example.deokgilserver.common.dto.MessageResponse;
import org.example.deokgilserver.common.security.RefreshTokenCookieProvider;
import org.example.deokgilserver.domain.auth.presentation.dto.request.GoogleAuthRequest;
import org.example.deokgilserver.domain.auth.presentation.dto.response.AuthResponse;
import org.example.deokgilserver.domain.auth.service.AuthService;
import org.example.deokgilserver.domain.auth.service.dto.RefreshResult;
import org.example.deokgilserver.domain.auth.service.dto.TokenResult;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    public AuthController(AuthService authService, RefreshTokenCookieProvider refreshTokenCookieProvider) {
        this.authService = authService;
        this.refreshTokenCookieProvider = refreshTokenCookieProvider;
    }

    @PostMapping("/signup/google")
    public AuthResponse signUpWithGoogle(@Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        TokenResult tokenResult = authService.signUpWithGoogle(request);
        setRefreshTokenCookie(response, tokenResult.refreshToken());
        return new AuthResponse(tokenResult.accessToken(), tokenResult.user());
    }

    @PostMapping("/login/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        TokenResult tokenResult = authService.loginWithGoogle(request);
        setRefreshTokenCookie(response, tokenResult.refreshToken());
        return new AuthResponse(tokenResult.accessToken(), tokenResult.user());
    }

    @PostMapping("/reissue")
    public AuthResponse reissue(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        RefreshResult refreshResult = authService.reissue(refreshToken);
        setRefreshTokenCookie(response, refreshResult.refreshToken());
        return new AuthResponse(refreshResult.accessToken(), null);
    }

    @PostMapping("/logout")
    public MessageResponse logout(@AuthenticationPrincipal UUID userId, HttpServletResponse response) {
        authService.logout(userId);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString());
        return new MessageResponse("로그아웃되었습니다.");
    }

    /**
     * CSRF 쿠키(XSRF-TOKEN)를 발급하기 위한 엔드포인트.
     * 클라이언트는 로그인 전 이 API를 한 번 호출해 쿠키를 확보하고,
     * 이후 /reissue, /logout 요청 시 쿠키 값을 X-XSRF-TOKEN 헤더에 담아 보내야 한다.
     */
    @GetMapping("/csrf-token")
    public void csrfToken(HttpServletRequest request) {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken();
        }
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.create(refreshToken).toString());
    }
}
