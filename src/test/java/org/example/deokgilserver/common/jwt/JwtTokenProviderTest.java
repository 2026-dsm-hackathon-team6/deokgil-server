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
    void 유효한_토큰은_검증에_성공한다() {
        String token = jwtTokenProvider.createAccessToken(UUID.randomUUID());

        assertThatCode_validateToken(token);
    }

    @Test
    void 만료된_토큰은_TOKEN_EXPIRED_예외가_발생한다() {
        JwtProperties expiredProperties = new JwtProperties(SECRET, -1000L, -1000L);
        JwtTokenProvider expiredTokenProvider = new JwtTokenProvider(expiredProperties);
        String expiredToken = expiredTokenProvider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(expiredToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.TOKEN_EXPIRED);
    }

    @Test
    void 위조된_토큰은_INVALID_TOKEN_예외가_발생한다() {
        String tamperedToken = jwtTokenProvider.createAccessToken(UUID.randomUUID()) + "tampered";

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tamperedToken))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 다른_비밀키로_서명된_토큰은_INVALID_TOKEN_예외가_발생한다() {
        JwtProperties otherProperties = new JwtProperties("another-completely-different-secret-key-value", 60000L, 60000L);
        JwtTokenProvider otherTokenProvider = new JwtTokenProvider(otherProperties);
        String tokenFromOtherIssuer = otherTokenProvider.createAccessToken(UUID.randomUUID());

        assertThatThrownBy(() -> jwtTokenProvider.validateToken(tokenFromOtherIssuer))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 형식이_올바르지_않은_토큰은_INVALID_TOKEN_예외가_발생한다() {
        assertThatThrownBy(() -> jwtTokenProvider.validateToken("not-a-jwt-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }

    private void assertThatCode_validateToken(String token) {
        jwtTokenProvider.validateToken(token);
    }
}
