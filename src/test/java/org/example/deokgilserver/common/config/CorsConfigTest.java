package org.example.deokgilserver.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

import static org.assertj.core.api.Assertions.assertThat;

class CorsConfigTest {

    private final CorsConfigurationSource source =
            new CorsConfig().corsConfigurationSource(new String[]{"http://localhost:5173"});

    @Test
    void 허용된_origin의_프리플라이트_요청에_CORS_헤더가_실린다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login/google");
        request.addHeader("Origin", "http://localhost:5173");
        request.addHeader("Access-Control-Request-Method", "POST");
        request.addHeader("Access-Control-Request-Headers", "Content-Type,Authorization");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CorsConfiguration config = source.getCorsConfiguration(request);
        boolean handled = new DefaultCorsProcessor().processRequest(config, request, response);

        assertThat(handled).isTrue();
        assertThat(response.getHeader("Access-Control-Allow-Origin")).isEqualTo("http://localhost:5173");
        assertThat(response.getHeader("Access-Control-Allow-Credentials")).isEqualTo("true");
        assertThat(response.getHeader("Access-Control-Allow-Methods")).contains("POST");
    }

    @Test
    void 허용되지_않은_origin은_CORS_헤더가_실리지_않는다() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/login/google");
        request.addHeader("Origin", "http://evil.example.com");
        request.addHeader("Access-Control-Request-Method", "POST");
        MockHttpServletResponse response = new MockHttpServletResponse();

        CorsConfiguration config = source.getCorsConfiguration(request);
        new DefaultCorsProcessor().processRequest(config, request, response);

        assertThat(response.getHeader("Access-Control-Allow-Origin")).isNull();
    }
}
