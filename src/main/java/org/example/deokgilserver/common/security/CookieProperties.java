package org.example.deokgilserver.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * secure=false는 로컬(http) 개발 전용이며, 운영에서는 반드시 true여야 한다(HTTPS가 아니면
 * 쿠키가 평문으로 오갈 수 있음). sameSite=Strict가 기본값으로 잡혀 있는데, 이는 다른 사이트에서
 * 걸어오는 요청에는 이 쿠키가 아예 실리지 않게 해서 CSRF 공격 표면을 1차로 줄여준다
 * (완전한 방어는 아니라서 SecurityConfig의 CSRF 토큰 검증이 별도로 필요하다).
 */
@ConfigurationProperties(prefix = "cookie")
public record CookieProperties(boolean secure, String sameSite, String domain) {
}
