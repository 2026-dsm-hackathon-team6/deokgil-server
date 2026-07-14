package org.example.deokgilserver.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void 비어있거나_형식이_잘못된_요청_본문은_400을_반환한다() {
        HttpMessageNotReadableException e = mock(HttpMessageNotReadableException.class);

        ResponseEntity<ErrorResponse> response = handler.handleHttpMessageNotReadableException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_INPUT.name());
    }

    @Test
    void 비즈니스_예외는_해당_에러코드의_상태로_반환된다() {
        BusinessException e = new BusinessException(ErrorCode.WITHDRAWN_USER);

        ResponseEntity<ErrorResponse> response = handler.handleBusinessException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.WITHDRAWN_USER.name());
    }

    @Test
    void 경로변수_타입_불일치는_400을_반환한다() {
        MethodArgumentTypeMismatchException e = mock(MethodArgumentTypeMismatchException.class);
        when(e.getName()).thenReturn("eventId");

        ResponseEntity<ErrorResponse> response = handler.handleMethodArgumentTypeMismatchException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_INPUT.name());
    }

    @Test
    void 필수_요청_파라미터_누락은_400을_반환한다() {
        MissingServletRequestParameterException e =
                new MissingServletRequestParameterException("code", "String");

        ResponseEntity<ErrorResponse> response = handler.handleMissingServletRequestParameterException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_INPUT.name());
    }

    @Test
    void 지원하지_않는_HTTP_메서드는_405를_반환한다() {
        HttpRequestMethodNotSupportedException e = new HttpRequestMethodNotSupportedException("GET");

        ResponseEntity<ErrorResponse> response = handler.handleHttpRequestMethodNotSupportedException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.METHOD_NOT_ALLOWED.name());
    }

    @Test
    void 예상치_못한_예외는_500을_반환한다() {
        RuntimeException e = new RuntimeException("boom");

        ResponseEntity<ErrorResponse> response = handler.handleException(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.name());
    }
}
