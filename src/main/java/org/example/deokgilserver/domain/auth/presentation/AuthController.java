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
import org.example.deokgilserver.domain.user.presentation.dto.request.ProfileImageUploadRequest;
import org.example.deokgilserver.domain.user.presentation.dto.response.PresignedProfileImageUploadResponse;
import org.example.deokgilserver.domain.user.service.UserService;
import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final RefreshTokenCookieProvider refreshTokenCookieProvider;

    public AuthController(
            AuthService authService,
            UserService userService,
            RefreshTokenCookieProvider refreshTokenCookieProvider
    ) {
        this.authService = authService;
        this.userService = userService;
        this.refreshTokenCookieProvider = refreshTokenCookieProvider;
    }

    @PostMapping("/signup/google")
    public AuthResponse signUpWithGoogle(@Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        TokenResult tokenResult = authService.signUpWithGoogle(request);
        setRefreshTokenCookie(response, tokenResult.refreshToken());
        return new AuthResponse(tokenResult.accessToken(), tokenResult.user());
    }

    // 회원가입 전(계정이 아직 없는 상태)에도 프로필 이미지를 미리 업로드할 수 있도록 presigned
    // URL을 발급한다 - "/api/v1/auth/signup/**" 패턴으로 SecurityConfig에서 permitAll이다.
    // 발급받은 imageUrl을 업로드 완료 후 signUpWithGoogle 요청의 profileImage에 담아 보내면 된다.
    @PostMapping("/signup/profile-image/presigned-url")
    public PresignedProfileImageUploadResponse createSignupProfileImageUploadUrl(
            @Valid @RequestBody ProfileImageUploadRequest request
    ) {
        return userService.createProfileImageUploadUrlForSignup(request.contentType());
    }

    @PostMapping("/login/google")
    public AuthResponse loginWithGoogle(@Valid @RequestBody GoogleAuthRequest request, HttpServletResponse response) {
        TokenResult tokenResult = authService.loginWithGoogle(request);
        setRefreshTokenCookie(response, tokenResult.refreshToken());
        return new AuthResponse(tokenResult.accessToken(), tokenResult.user());
    }

    // SecurityConfig에서 permitAll이라 Authorization 헤더(access token) 없이도 호출 가능하다 —
    // 애초에 access token이 만료됐을 때 새로 받으려고 부르는 API이므로 그게 맞다. 대신 refresh
    // token(쿠키) 자체의 유효성은 AuthService.reissue 내부에서 별도로 검증된다.
    @PostMapping("/reissue")
    public AuthResponse reissue(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        RefreshResult refreshResult = authService.reissue(refreshToken);
        setRefreshTokenCookie(response, refreshResult.refreshToken());
        return new AuthResponse(refreshResult.accessToken(), null);
    }

    private static final String BEARER_PREFIX = "Bearer ";

    // access token 유무/만료와 무관하게 항상 호출 가능하다(SecurityConfig에서 permitAll) -
    // 신원 확인은 refresh token 쿠키를 우선 쓰고, 쿠키가 없으면(예: 프론트/백엔드가 서로 다른
    // 사이트라 SameSite 정책상 쿠키 자체가 전송되지 않는 배포) Authorization 헤더로 폴백한다.
    // AuthServiceImpl.logout 참고.
    @PostMapping("/logout")
    public MessageResponse logout(
            @CookieValue(name = RefreshTokenCookieProvider.COOKIE_NAME, required = false) String refreshToken,
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken, extractBearerToken(authorizationHeader));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieProvider.delete().toString());
        return new MessageResponse("로그아웃되었습니다.");
    }

    private String extractBearerToken(String authorizationHeader) {
        if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith(BEARER_PREFIX)) {
            return authorizationHeader.substring(BEARER_PREFIX.length());
        }
        return null;
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
