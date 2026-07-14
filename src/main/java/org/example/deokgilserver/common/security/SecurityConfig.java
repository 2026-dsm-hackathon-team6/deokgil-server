package org.example.deokgilserver.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.exception.ErrorResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String[] SWAGGER_PATHS = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private static final String[] PERMIT_ALL_PATHS = {
            "/api/v1/auth/signup/**",
            "/api/v1/auth/login/**",
            "/api/v1/auth/reissue",
            "/api/v1/auth/csrf-token"
    };

    /**
     * refresh token 쿠키만으로 상태를 변경하는 엔드포인트. 브라우저가 쿠키를 자동으로 실어 보내므로
     * CSRF 토큰 검증을 반드시 거쳐야 한다. Authorization 헤더 기반 API는 브라우저가 헤더를
     * 자동으로 붙이지 않으므로 CSRF 대상에서 제외한다.
     */
    private static final String[] CSRF_PROTECTED_PATHS = {
            "/api/v1/auth/reissue",
            "/api/v1/auth/logout"
    };

    /**
     * Spring Boot 4.1 기준 spring-boot-autoconfigure에 Jackson 자동 설정이 더 이상 포함되어 있지 않아
     * 앱 전역 ObjectMapper 빈을 신뢰할 수 없다(존재 여부/직렬화 설정이 버전에 따라 달라질 수 있음).
     * 인증 실패 응답(ErrorResponse)만 직렬화하는 용도이므로 별도 빈에 의존하지 않고 직접 소유한다.
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    /**
     * Swagger UI 전용 체인. 실제 API(default-src 'none')와 달리 Swagger UI는 자체 번들 JS/CSS를
     * 그려야 하므로 CSP를 완화한다. 이 체인은 /v3/api-docs, /swagger-ui/** 요청에만 적용되고
     * JWT 필터도 타지 않는다. 운영 배포 시에는 SWAGGER_ENABLED=false 로 아예 노출을 막을 것.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(SWAGGER_PATHS)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .headers(headers -> headers
                        .frameOptions(frame -> frame.deny())
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "connect-src 'self'"
                        ))
                );

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher())
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PERMIT_ALL_PATHS).permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(ErrorCode.UNAUTHENTICATED.getStatus().value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write(
                                    OBJECT_MAPPER.writeValueAsString(ErrorResponse.of(ErrorCode.UNAUTHENTICATED)));
                        })
                )
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private RequestMatcher csrfProtectionMatcher() {
        RequestMatcher[] matchers = new RequestMatcher[CSRF_PROTECTED_PATHS.length];
        for (int i = 0; i < CSRF_PROTECTED_PATHS.length; i++) {
            matchers[i] = PathPatternRequestMatcher.pathPattern(HttpMethod.POST, CSRF_PROTECTED_PATHS[i]);
        }
        return new OrRequestMatcher(matchers);
    }
}
