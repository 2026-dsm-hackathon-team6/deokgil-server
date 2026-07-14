package org.example.deokgilserver.common.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * secret은 HMAC 서명 키의 원천이다. 이 값이 유출되면 누구나 임의의 userId로 유효한 토큰을
 * 위조할 수 있으므로(서버 DB 조회 없이도 위조 토큰이 서명 검증을 통과함) 절대 커밋되어선 안 되고,
 * 충분한 엔트로피(최소 256bit 권장, HS256 기준)를 가져야 한다.
 * access/refresh 만료 시간을 크게 벌려 둔 이유: access token은 탈취돼도 피해 창을 짧게 하려고
 * 짧게(예: 30분), refresh token은 재로그인 빈도를 줄이려고 길게(예: 14일) 잡는 것이 일반적인 트레이드오프.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtProperties(String secret, long accessTokenExpiration, long refreshTokenExpiration) {
}
