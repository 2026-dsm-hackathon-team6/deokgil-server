package org.example.deokgilserver.domain.auth.repository;

import org.example.deokgilserver.common.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * userId당 refresh token을 "딱 하나"만 저장한다(set으로 덮어쓰기). 그래서 재발급 시 새 토큰을
 * save()하면 이전 토큰은 자동으로 무효화되고, 그게 곧 rotation이다 — JWT 자체는 무상태라
 * 서버가 폐기를 표현할 수 없으니, "Redis의 최신 값과 일치하는가"로 유효성을 대신 판단하는 것.
 * 주의: 슬롯이 사용자당 1개뿐이라 여러 기기에서 동시 로그인하면 나중 로그인이 이전 기기의 refresh
 * token을 덮어써 무효화시킨다(다중 기기 동시 로그인은 지원하지 않는 구조). 또한 탈취된 토큰이
 * 재사용되는 걸 감지는 하지만(matches 실패), 그 시점에 다른 정상 세션들까지 전부 폐기하는
 * "토큰 패밀리(token family) 무효화"까지는 하지 않는다 — 탈취 흔적이 있어도 공격자의 최신
 * 토큰이 이미 있다면 그 토큰으로는 계속 접근 가능하다는 뜻.
 */
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
