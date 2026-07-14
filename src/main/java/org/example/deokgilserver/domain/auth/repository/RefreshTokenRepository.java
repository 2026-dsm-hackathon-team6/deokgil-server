package org.example.deokgilserver.domain.auth.repository;

import org.example.deokgilserver.common.jwt.JwtProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * userId당 refresh token을 "딱 하나"만 저장한다(set으로 덮어쓰기). 그래서 재발급 시 rotate()로
 * 새 토큰을 덮어쓰면 이전 토큰은 자동으로 무효화되고, 그게 곧 rotation이다 — JWT 자체는 무상태라
 * 서버가 폐기를 표현할 수 없으니, "Redis의 최신 값과 일치하는가"로 유효성을 대신 판단하는 것.
 * 주의: 슬롯이 사용자당 1개뿐이라 여러 기기에서 동시 로그인하면 나중 로그인이 이전 기기의 refresh
 * token을 덮어써 무효화시킨다(다중 기기 동시 로그인은 지원하지 않는 구조). 또한 탈취된 토큰이
 * 재사용되는 걸 감지는 하지만(rotate 실패), 그 시점에 다른 정상 세션들까지 전부 폐기하는
 * "토큰 패밀리(token family) 무효화"까지는 하지 않는다 — 탈취 흔적이 있어도 공격자의 최신
 * 토큰이 이미 있다면 그 토큰으로는 계속 접근 가능하다는 뜻.
 */
@Repository
public class RefreshTokenRepository {

    private static final String KEY_PREFIX = "refresh-token:";

    /**
     * "저장된 값이 oldToken과 같으면 newToken으로 교체" 를 원자적으로 수행한다. GET 후 SET을
     * 애플리케이션 코드에서 따로 하면 그 사이에 동시 요청이 끼어들어 둘 다 검증을 통과해버리는
     * TOCTOU(race condition)가 생긴다 — 같은 refresh token으로 거의 동시에 두 번 재발급을
     * 요청하면 둘 다 성공해버릴 수 있다는 뜻. Lua 스크립트는 Redis 서버에서 단일 커맨드처럼
     * 원자적으로 실행되므로 이 틈이 없다.
     */
    private static final DefaultRedisScript<Long> COMPARE_AND_SET_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "redis.call('set', KEYS[1], ARGV[2], 'PX', ARGV[3]) "
                    + "return 1 "
                    + "else "
                    + "return 0 "
                    + "end",
            Long.class
    );

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

    /**
     * 검증에 쓰인 oldToken이 여전히 최신 값일 때만 newToken으로 교체한다. 동시에 들어온 두
     * 재발급 요청 중 하나만 이 교체에 성공하고, 나머지는 false를 받아 거부된다(재사용 탐지).
     */
    public boolean rotate(UUID userId, String oldToken, String newToken) {
        Long result = redisTemplate.execute(
                COMPARE_AND_SET_SCRIPT,
                Collections.singletonList(KEY_PREFIX + userId),
                oldToken,
                newToken,
                String.valueOf(jwtProperties.refreshTokenExpiration())
        );
        return Long.valueOf(1L).equals(result);
    }

    public void delete(UUID userId) {
        redisTemplate.delete(KEY_PREFIX + userId);
    }
}
