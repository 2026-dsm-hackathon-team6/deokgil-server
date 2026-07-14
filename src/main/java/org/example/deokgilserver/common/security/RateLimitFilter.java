package org.example.deokgilserver.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.exception.ErrorResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

/**
 * 로그인/재발급처럼 무차별 대입(brute force)이나 남용의 표적이 되기 쉬운 엔드포인트에
 * IP 기준 고정 윈도우(fixed window) 방식으로 요청 횟수를 제한한다. Redis INCR의 원자성을
 * 이용해 별도 락 없이 동시 요청에서도 카운트가 정확하게 유지된다.
 *
 * 주의: 클라이언트 IP는 X-Forwarded-For 헤더를 신뢰할 수 있는 리버스 프록시/로드밸런서
 * 뒤에 있을 때만 정확하다. 그런 프록시 없이 이 헤더를 그대로 신뢰하면 클라이언트가 헤더
 * 값을 조작해 레이트리밋을 우회할 수 있으므로, 운영 배포 시 프록시가 이 헤더를 항상
 * 덮어쓰도록 설정되어 있는지 반드시 확인해야 한다.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    // 경로별 "윈도우(초) 동안 허용할 최대 요청 수". 인증 관련 엔드포인트만 우선 제한한다 —
    // 계정 탈취 시도(로그인 무차별 대입)나 리프레시 토큰 재사용 스캔이 가장 흔한 남용 패턴이기 때문이다.
    private static final Map<String, Limit> LIMITS = Map.of(
            "/api/v1/auth/login/google", new Limit(10, Duration.ofMinutes(1)),
            "/api/v1/auth/signup/google", new Limit(5, Duration.ofMinutes(1)),
            "/api/v1/auth/reissue", new Limit(20, Duration.ofMinutes(1))
    );

    private static final String KEY_PREFIX = "rate-limit:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = LIMITS.get(request.getRequestURI());

        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = KEY_PREFIX + request.getRequestURI() + ":" + resolveClientIp(request);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, limit.window());
        }

        if (count != null && count > limit.maxRequests()) {
            respondTooManyRequests(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ErrorResponse.of(ErrorCode.RATE_LIMIT_EXCEEDED)));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private record Limit(int maxRequests, Duration window) {
    }
}
