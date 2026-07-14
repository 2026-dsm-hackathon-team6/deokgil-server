package org.example.deokgilserver.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.exception.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/**
 * 로그인/재발급처럼 무차별 대입(brute force)이나 남용의 표적이 되기 쉬운 엔드포인트, 그리고
 * Anthropic/카카오/기상청 같은 외부 유료 API를 호출하는 엔드포인트에 IP 기준 고정 윈도우
 * (fixed window) 방식으로 요청 횟수를 제한한다. Redis INCR의 원자성을 이용해 별도 락 없이
 * 동시 요청에서도 카운트가 정확하게 유지된다.
 *
 * IP 기준 제한의 한계: 이 필터는 JwtAuthenticationFilter보다 먼저 실행되므로(SecurityConfig)
 * 이 시점엔 인증된 사용자 식별자가 없어 사용자 단위 쿼터는 걸 수 없다 — 사용자별 쿼터가
 * 필요해지면 이 필터를 JWT 인증 이후로 옮기거나 별도 필터로 분리해야 한다.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    // (HTTP 메서드, 경로 패턴, 제한) — 경로에 {eventId} 같은 변수가 들어가는 엔드포인트가 있어
    // 문자열 완전 일치 대신 Ant 스타일 패턴 매칭을 사용한다.
    private static final List<RateLimitRule> RULES = List.of(
            new RateLimitRule("POST", "/api/v1/auth/login/google", new Limit(10, Duration.ofMinutes(1))),
            new RateLimitRule("POST", "/api/v1/auth/signup/google", new Limit(5, Duration.ofMinutes(1))),
            new RateLimitRule("POST", "/api/v1/auth/reissue", new Limit(20, Duration.ofMinutes(1))),
            // 아래는 전부 Anthropic/카카오/기상청 등 비용이 드는 외부 API를 호출하는 엔드포인트다.
            new RateLimitRule("POST", "/api/v1/events/extract", new Limit(10, Duration.ofMinutes(1))),
            new RateLimitRule("POST", "/api/v1/events/{eventId}/checklist", new Limit(10, Duration.ofMinutes(1))),
            new RateLimitRule("POST", "/api/v1/events/{eventId}/schedules/generate", new Limit(10, Duration.ofMinutes(1))),
            new RateLimitRule("GET", "/api/v1/events/{eventId}/briefing", new Limit(20, Duration.ofMinutes(1))),
            new RateLimitRule("GET", "/api/v1/events/{eventId}/map", new Limit(20, Duration.ofMinutes(1))),
            new RateLimitRule("POST", "/api/v1/routes/recommend", new Limit(20, Duration.ofMinutes(1)))
    );

    private static final String KEY_PREFIX = "rate-limit:";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    // 신뢰할 수 있는 리버스 프록시(Nginx/ALB 등)가 X-Forwarded-For를 항상 덮어쓰는 배포에서만
    // true로 켠다. 기본값 false: 인터넷에서 직접 접근 가능하거나 프록시 설정을 보장할 수 없다면,
    // 클라이언트가 마음대로 조작 가능한 이 헤더를 신뢰하지 않고 소켓의 실제 접속 IP만 사용해야
    // 레이트리밋 우회를 막을 수 있다.
    private final boolean trustForwardedFor;
    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(
            StringRedisTemplate redisTemplate,
            @Value("${rate-limit.trust-forwarded-for:false}") boolean trustForwardedFor
    ) {
        this.redisTemplate = redisTemplate;
        this.trustForwardedFor = trustForwardedFor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Limit limit = matchLimit(request);

        if (limit == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = KEY_PREFIX + request.getMethod() + ":" + request.getRequestURI() + ":" + resolveClientIp(request);
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

    private Limit matchLimit(HttpServletRequest request) {
        for (RateLimitRule rule : RULES) {
            if (rule.method().equals(request.getMethod())
                    && PATH_MATCHER.match(rule.pathPattern(), request.getRequestURI())) {
                return rule.limit();
            }
        }
        return null;
    }

    private void respondTooManyRequests(HttpServletResponse response) throws IOException {
        response.setStatus(ErrorCode.RATE_LIMIT_EXCEEDED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ErrorResponse.of(ErrorCode.RATE_LIMIT_EXCEEDED)));
    }

    /**
     * X-Forwarded-For는 신뢰 설정(rate-limit.trust-forwarded-for)이 켜져 있을 때만 사용하고,
     * 그마저도 유효한 IPv4/IPv6 형태일 때만 인정한다 — 형식이 아닌 임의의 문자열(예: 매 요청마다
     * 바뀌는 난수)을 그대로 Redis 키에 쓰면 카운터 우회는 물론 키가 무한히 생성되는 자원 고갈로
     * 이어질 수 있기 때문이다. 신뢰 설정이 꺼져 있거나 헤더가 유효하지 않으면 소켓의 실제 접속
     * IP(getRemoteAddr)를 쓴다.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwardedFor = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwardedFor)) {
                String candidate = forwardedFor.split(",")[0].trim();
                if (isValidIpAddress(candidate)) {
                    return candidate;
                }
            }
        }
        return request.getRemoteAddr();
    }

    // 순수 문자열 패턴만으로 판정한다 — InetAddress.getByName()은 호스트명을 DNS로 해석하려
    // 시도할 수 있어(블로킹 I/O), 공격자가 X-Forwarded-For에 임의 호스트명을 넣어 요청마다
    // DNS 조회를 유발하는 자원 고갈 벡터가 될 수 있다.
    private static final java.util.regex.Pattern IPV4_PATTERN = java.util.regex.Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$");
    private static final java.util.regex.Pattern IPV6_PATTERN = java.util.regex.Pattern.compile("^[0-9a-fA-F:]+$");

    private boolean isValidIpAddress(String value) {
        if (value.isEmpty() || value.length() > 45) {
            return false;
        }
        return IPV4_PATTERN.matcher(value).matches()
                || (value.contains(":") && IPV6_PATTERN.matcher(value).matches());
    }

    private record RateLimitRule(String method, String pathPattern, Limit limit) {
    }

    private record Limit(int maxRequests, Duration window) {
    }
}
