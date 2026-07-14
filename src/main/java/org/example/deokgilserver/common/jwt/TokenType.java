package org.example.deokgilserver.common.jwt;

// access token과 refresh token은 서명 키·페이로드 구조가 동일해서, 이 타입 클레임이 없으면
// 탈취한 refresh token을 그대로 Authorization 헤더에 실어 일반 API를 호출할 수 있다
// (JwtAuthenticationFilter는 서명/만료만 보고 어떤 토큰이든 인증으로 인정하기 때문).
// 발급 시 이 클레임을 심고, 검증 시 기대 타입과 일치하는지 반드시 확인한다.
public enum TokenType {
    ACCESS,
    REFRESH
}
