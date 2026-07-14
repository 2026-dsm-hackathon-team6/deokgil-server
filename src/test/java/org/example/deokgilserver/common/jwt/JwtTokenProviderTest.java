package org.example.deokgilserver.common.jwt;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtTokenProviderTest {

    private static final String SECRET = "test-jwt-secret-key-that-is-long-enough-for-hs256";

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        JwtProperties jwtProperties = new JwtProperties(SECRET, 1000L * 60, 1000L * 60 * 60);
        jwtTokenProvider = new JwtTokenProvider(jwtProperties);
    }

    @Test
    void 액세스_토큰을_생성하고_사용자_ID를_추출할_수_있다() {
        UUID userId = UUID.randomUUID();

        String accessToken = jwtTokenProvider.createAccessToken(userId);

        assertThat(accessToken).isNotBlank();
        assertThat(jwtTokenProvider.getUserId(accessToken)).isEqualTo(userId);
    }

    @Test
    void 리프레시_토큰을_생성하고_사용자_ID를_추출할_수_있다() {
        UUID userId = UUID.randomUUID();

        String refreshToken = jwtTokenProvider.createRefreshToken(userId);

        assertThat(refreshToken).isNotBlank();
        assertThat(jwtTokenProvider.getUserId(refreshToken)).isEqualTo(userId);
    }

    @Test
    void 액세스_토큰은_ACCESS_타입_검증에_성공한다() {
        String token = jwtTokenProvider.createAccessToken(UUID.randomUUID());

        jwtTokenProvider.validateToken(token, TokenType.ACCESS);
    }

    @Test
    void 리프레시_토큰은_REFRESH_타입_검증에_성공한다() {
        String token = jwtTokenProvider.createRefreshToken(UUID.randomUUID());

        jwtTokenProvider.validateToken(token, TokenType.REFRESH);
    }

    // 이 테스트가 이번 수정의 핵심이다: 탈취한 refresh token을 Authorization 헤더에 그대로
    // 실어 access token 대신 쓰는 공격을 막는지 확인한다.
    @Test
    void 리프레시_토큰으로_액세스_토큰_검증을_시도하면_INVALID_TOKEN_예외가_발생한다() {
        String refreshToken = jwtTokenProvider.createRefreshToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(refreshToken, TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 액세스_토큰으로_리프레시_토큰_검증을_시도하면_INVALID_TOKEN_예외가_발생한다() {
        String accessToken = jwtTokenProvider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(accessToken, TokenType.REFRESH))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 만료된_토큰은_TOKEN_EXPIRED_예외가_발생한다() {
        JwtProperties expiredProperties = new JwtProperties(SECRET, -1000L, -1000L);
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(expiredProperties);
        String expiredToken = expiredTokenProvider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken, TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 위조된_토큰은_INVALID_TOKEN_예외가_발생한다() {
        String tamperedToken = jwtTokenProvider.createAccessToken(UUID.randomUUID()) + "tampered";

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken, TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 다른_비밀키로_서명된_토큰은_INVALID_TOKEN_예외가_발생한다() {
        JwtProperties otherProperties = new JwtProperties("another-completely-different-secret-key-value", 60000L, 60000L);
        JwtTokenProvider otherTokenProvider = new JwtTokenProvider(otherProperties);
        String tokenFromOtherIssuer = otherTokenProvider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tokenFromOtherIssuer, TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 형식이_올바르지_않은_토큰은_INVALID_TOKEN_예외가_발생한다() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken("not-a-jwt-token", TokenType.ACCESS))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
