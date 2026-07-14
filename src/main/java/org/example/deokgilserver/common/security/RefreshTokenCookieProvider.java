package org.example.deokgilserver.common.security;

import org.example.deokgilserver.common.jwt.JwtProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class RefreshTokenCookieProvider {

    public static final String COOKIE_NAME = "refresh_token";
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
