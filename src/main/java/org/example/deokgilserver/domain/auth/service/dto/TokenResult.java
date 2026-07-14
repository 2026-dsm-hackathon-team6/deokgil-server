package org.example.deokgilserver.domain.auth.service.dto;

import org.example.deokgilserver.domain.user.presentation.dto.response.UserResponse;

/**
 * 회원가입/로그인 직후 발급되는 토큰 쌍. refreshToken은 컨트롤러가 HttpOnly 쿠키로만 내려보내고
 * 응답 바디(AuthResponse)에는 절대 포함하지 않는다.
 */
public record TokenResult(String accessToken, String refreshToken, UserResponse user) {
}
