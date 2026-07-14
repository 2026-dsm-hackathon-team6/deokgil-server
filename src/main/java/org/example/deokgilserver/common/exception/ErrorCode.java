package org.example.deokgilserver.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "존재하지 않는 사용자입니다."),
    DUPLICATE_USER(HttpStatus.CONFLICT, "이미 가입된 사용자입니다."),
    WITHDRAWN_USER(HttpStatus.FORBIDDEN, "탈퇴한 사용자입니다."),
    GOOGLE_AUTH_FAILED(HttpStatus.UNAUTHORIZED, "구글 인증에 실패했습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "인증 시간이 만료되었습니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP 메서드입니다."),

    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다."),
    EVENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "이벤트에 대한 권한이 없습니다."),
    EVENT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 삭제된 이벤트입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 일정이 없습니다."),

    INVALID_URL(HttpStatus.BAD_REQUEST, "올바르지 않은 URL입니다."),
    UNSUPPORTED_SITE(HttpStatus.BAD_REQUEST, "지원하지 않는 사이트입니다."),
    EXTRACTION_FAILED(HttpStatus.BAD_REQUEST, "행사 정보를 추출할 수 없습니다."),
    MISSING_EVENT_INFO(HttpStatus.BAD_REQUEST, "필수 행사 정보가 부족합니다."),
    AI_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 분석 중 오류가 발생했습니다."),

    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
