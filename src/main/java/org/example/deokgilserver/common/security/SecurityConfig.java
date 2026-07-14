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
    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, RateLimitFilter rateLimitFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitFilter = rateLimitFilter;
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
                // CorsConfig가 정의한 CorsConfigurationSource 빈을 사용한다. CORS 자체는 "이 요청을
                // 허용할지" 브라우저에게 알려주는 응답 헤더일 뿐 인증과 무관하므로, 아래
                // authorizeHttpRequests에서 프리플라이트(OPTIONS)를 별도로 permitAll 해줘야
                // 실제 요청 전에 브라우저가 보내는 예비 확인 요청이 401로 막히지 않는다.
                .cors(Customizer.withDefaults())
                // 더블 서브밋 쿠키(double-submit cookie) 패턴: 서버가 CSRF 토큰을 (httpOnly=false)
                // 쿠키로 내려주면, 같은 출처(same-origin)의 JS만 그 값을 읽어 X-XSRF-TOKEN 헤더에
                // 실을 수 있다. 공격자 사이트는 쿠키 값을 읽을 수 없으니 헤더를 위조하지 못한다 —
                // 브라우저가 쿠키만 자동으로 실어 보내는 요청(폼 제출 등)과 값을 구별하는 것이 핵심.
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        .requireCsrfProtectionMatcher(csrfProtectionMatcher())
                )
                // 서버가 로그인 세션을 들고 있지 않음(JSESSIONID 없음). 인증 상태는 매 요청마다
                // JwtAuthenticationFilter가 토큰만 보고 재구성하므로, 세션 하이재킹/세션 고정
                // (session fixation) 같은 세션 기반 공격 자체가 성립하지 않는다.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
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
                        // X-Content-Type-Options: nosniff — 브라우저가 응답을 선언된 Content-Type과
                        // 다르게 추측(MIME sniffing)해서 실행하는 것을 막는다.
                        .contentTypeOptions(Customizer.withDefaults())
                        // clickjacking 방지: 이 API 응답이 다른 사이트의 <iframe>에 렌더링되지 못하게 한다.
                        .frameOptions(frame -> frame.deny())
                        // HSTS: 브라우저가 이후 요청을 강제로 HTTPS로만 보내게 해서, 최초 접속 이후엔
                        // http로의 다운그레이드나 SSL-strip 공격을 막는다. includeSubDomains로 서브도메인도 적용.
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                        // Referer 헤더에 담기는 정보를 교차 출처 요청에서는 origin까지만 노출시켜
                        // 쿼리 파라미터 등 민감할 수 있는 경로 정보가 제3자 사이트로 새는 것을 줄인다.
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        // API 전용 서버라 브라우저가 렌더링할 리소스가 없어야 정상이므로 전부 차단(default-src 'none').
                        // 혹시 XSS로 스크립트 삽입이 성공해도 외부 스크립트/이미지 로드, iframe 삽입 등을 CSP가 막아준다.
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                )
                // Spring Security의 기본 인증 필터(폼 로그인용) 이전에 우리 JWT 필터를 끼워 넣어서,
                // 매 요청마다 토큰 기반 인증이 먼저 시도되도록 한다.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // 레이트리밋은 JWT 인증/CSRF 검증보다도 먼저 걸려야 한다 — 어차피 거부할 요청에
                // 토큰 파싱이나 CSRF 토큰 비교 같은 비용을 들이지 않기 위해서다.
                .addFilterBefore(rateLimitFilter, JwtAuthenticationFilter.class);

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
