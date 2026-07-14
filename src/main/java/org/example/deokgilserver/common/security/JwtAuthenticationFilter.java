package org.example.deokgilserver.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.deokgilserver.common.jwt.JwtTokenProvider;
import org.example.deokgilserver.common.jwt.TokenType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * access token만 검사하는 필터다. Authorization 헤더는 브라우저가 자동으로 실어 보내지 않고
 * 클라이언트(JS)가 명시적으로 붙여야 하므로, 세션 쿠키 기반 인증과 달리 이 헤더 자체는 CSRF에
 * 노출되지 않는다(CSRF는 "브라우저가 자동으로 실어 보내는 자격증명"을 악용하는 공격이기 때문).
 * 반대로 XSS에는 취약할 수 있어, access token은 만료 시간을 짧게 유지하는 것으로 위험을 상쇄한다.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String token = resolveToken(request);

        if (StringUtils.hasText(token)) {
            try {
                jwtTokenProvider.validateToken(token, TokenType.ACCESS);
                UUID userId = jwtTokenProvider.getUserId(token);

                // 별도 인가(authority) 개념 없이 인증 여부만 판단한다(List.of()로 권한 목록 비움).
                // 이 서버는 세션을 두지 않으므로(STATELESS) 매 요청마다 이 과정을 반복해서
                // SecurityContext를 새로 채운다 — 서버 재시작/스케일아웃에도 인증 상태가 영향받지 않는다.
                var authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception e) {
                // 토큰이 없거나 위조/만료됐어도 여기서 요청을 막지 않고 그냥 미인증 상태로 통과시킨다.
                // 실제 차단은 SecurityConfig의 authorizeHttpRequests(인가 규칙)가 담당하므로,
                // permitAll 경로는 토큰이 잘못돼도 정상 동작해야 한다.
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
