package org.example.deokgilserver.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * 프론트엔드(별도 origin: 로컬 Vite 개발 서버, 실제 배포 도메인)가 쿠키(refresh token, CSRF)를
 * 실어 API를 호출할 수 있게 하는 CORS 설정.
 *
 * allowedOrigins는 반드시 명시적 목록이어야 한다 — allowCredentials=true(쿠키 전송 허용)와
 * allowedOrigins("*")는 브라우저가 애초에 동시 사용을 금지한다(CORS 스펙 위반). 그래서
 * 와일드카드 대신 CORS_ALLOWED_ORIGINS 환경변수로 허용할 origin을 콤마로 구분해 받는다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${cors.allowed-origins}") String[] allowedOrigins
    ) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList(allowedOrigins));
        configuration.setAllowedMethods(List.of("GET", "POST", "PATCH", "PUT", "DELETE", "OPTIONS"));
        // Authorization: access token(Bearer) 전송에 필요.
        // X-XSRF-TOKEN: 더블 서브밋 쿠키 방식 CSRF 검증에 필요(SecurityConfig 참고).
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        // refresh token 쿠키, CSRF 쿠키를 브라우저가 자동으로 실어 보내려면 필수.
        configuration.setAllowCredentials(true);
        // 프리플라이트(OPTIONS) 응답을 캐싱해서, 매 요청마다 프리플라이트가 왕복하지 않게 한다.
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
