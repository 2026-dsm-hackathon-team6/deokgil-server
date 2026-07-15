package org.example.deokgilserver.common.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RateLimitFilter(redisTemplate, false);
    }

    /**
     * HttpServletRequest.getRequestURI()는 퍼센트 인코딩을 디코딩하지 않은 원본을 반환하는데,
     * Spring MVC는 라우팅 시 디코딩된 경로로 매칭한다 - 이 필터가 getRequestURI()를 그대로
     * 쓰면 "re%69ssue"(디코딩하면 reissue) 같은 요청이 컨트롤러에는 정상 라우팅되면서도
     * 필터의 문자열 매칭만 실패해 레이트리밋이 통째로 우회된다. UrlPathHelper로 디코딩한
     * 경로를 쓰면 이런 요청도 정상적으로 규칙에 매칭돼야 한다.
     */
    @Test
    void 퍼센트_인코딩된_경로도_디코딩해서_레이트리밋_규칙에_매칭된다() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/re%69ssue");
        request.setRequestURI("/api/v1/auth/re%69ssue");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(valueOperations).increment(contains("/api/v1/auth/reissue"));
    }

    @Test
    void 규칙에_없는_경로는_카운트하지_않고_그대로_통과한다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events/list");
        request.setRequestURI("/api/v1/events/list");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
