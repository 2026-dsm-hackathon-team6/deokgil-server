package org.example.deokgilserver.domain.auth.repository;

import org.example.deokgilserver.common.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh-token:";

    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;

    public RefreshTokenRepository(StringRedisTemplate redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    public void save(UUID userId, String refreshToken) {
        redisTemplate.opsForValue().set(
                KEY_PREFIX + userId,
                refreshToken,
                Duration.ofMillis(jwtProperties.refreshTokenExpiration())
        );
    }

    public Optional<String> findByUserId(UUID userId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + userId));
    }

    /**
     * Redis에 저장된 최신 refresh token과 일치하는지 확인한다.
     * 로그아웃 등으로 폐기되었거나, 이미 회전(rotate)되어 재사용된 토큰을 거부하기 위함이다.
     */
    public boolean matches(UUID userId, String refreshToken) {
        return findByUserId(userId)
                .map(stored -> stored.equals(refreshToken))
                .orElse(false);
    }

    public void delete(UUID userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
