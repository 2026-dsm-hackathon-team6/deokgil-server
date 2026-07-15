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
    EVENT_NOT_ENDED(HttpStatus.BAD_REQUEST, "아직 종료되지 않은 행사입니다."),
    SCHEDULE_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 일정이 없습니다."),

    INVALID_URL(HttpStatus.BAD_REQUEST, "올바르지 않은 URL입니다."),
    UNSUPPORTED_SITE(HttpStatus.BAD_REQUEST, "지원하지 않는 사이트입니다."),
    EXTRACTION_FAILED(HttpStatus.BAD_REQUEST, "행사 정보를 추출할 수 없습니다."),
    MISSING_EVENT_INFO(HttpStatus.BAD_REQUEST, "필수 행사 정보가 부족합니다."),
    AI_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "AI 분석 중 오류가 발생했습니다."),

    EVENT_LOCATION_REQUIRED(HttpStatus.BAD_REQUEST, "행사 위치 정보가 없어 체크리스트를 생성할 수 없습니다."),
    GEOCODING_FAILED(HttpStatus.BAD_REQUEST, "행사 주소의 위치를 확인할 수 없습니다."),
    WEATHER_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "날씨 정보를 가져오는 중 오류가 발생했습니다."),
    AI_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "AI 생성에 실패했습니다."),

    EVENT_ALREADY_STARTED(HttpStatus.BAD_REQUEST, "이미 시작된 행사입니다."),
    SCHEDULE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "이미 생성된 일정이 있습니다."),
    SCHEDULE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "일정에 대한 권한이 없습니다."),
    SCHEDULE_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "이미 삭제된 일정입니다."),
    INVALID_SCHEDULE_LIST(HttpStatus.BAD_REQUEST, "수정할 일정 정보가 없습니다."),
    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "일정 시간이 올바르지 않습니다."),
    SCHEDULE_OVERLAP(HttpStatus.BAD_REQUEST, "일정 시간이 중복됩니다."),

    INVALID_LOCATION(HttpStatus.BAD_REQUEST, "위치 정보가 올바르지 않습니다."),
    ROUTE_GENERATION_FAILED(HttpStatus.BAD_REQUEST, "동선 생성에 실패했습니다."),
    MAP_API_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "지도 API 호출 중 오류가 발생했습니다."),
    LOCATION_NOT_FOUND(HttpStatus.NOT_FOUND, "행사장 위치 정보가 없습니다."),

    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청이 너무 많습니다. 잠시 후 다시 시도해주세요."),

    UNSUPPORTED_IMAGE_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 이미지 형식입니다."),

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
