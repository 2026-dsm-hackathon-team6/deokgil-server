package org.example.deokgilserver.common.security;

import org.example.deokgilserver.common.jwt.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * refresh token을 access token과 달리 쿠키로 내려주는 이유: httpOnly 쿠키는 JS(document.cookie)로
 * 읽을 수 없어서, XSS로 스크립트가 실행되더라도 refresh token 자체는 탈취되지 않는다
 * (반면 access token은 JS가 Authorization 헤더에 직접 실어야 하니 메모리에 두고, 그래서 만료를 짧게 간다).
 */
@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refresh_token";
    // 이 쿠키가 /api/v1/auth 경로에만 실리도록 스코프를 좁힌다 — 다른 API 요청에는 자동으로
    // 붙지 않으므로, 그만큼 CSRF/탈취 공격이 노릴 수 있는 요청 범위가 줄어든다.
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final JwtProperties jwtProperties;
    private final CookieProperties cookieProperties;

    public RefreshTokenCookieProvider(JwtProperties jwtProperties, CookieProperties cookieProperties) {
        this.jwtProperties = jwtProperties;
        this.cookieProperties = cookieProperties;
    }

    public ResponseCookie create(String refreshToken) {
        return baseCookie(refreshToken)
                .maxAge(Duration.ofMillis(jwtProperties.refreshTokenExpiration()))
                .build();
    }

    // 값은 비우고 만료 시간을 0으로 줘서 브라우저가 즉시 쿠키를 삭제하게 만드는, 삭제 목적 전용 쿠키다.
    public ResponseCookie delete() {
        return baseCookie("")
                .maxAge(0)
                .build();
    }

    private ResponseCookie.ResponseCookieBuilder baseCookie(String value) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .path(COOKIE_PATH);

        if (StringUtils.hasText(cookieProperties.domain())) {
            builder.domain(cookieProperties.domain());
        }
        return builder;
    }
}
