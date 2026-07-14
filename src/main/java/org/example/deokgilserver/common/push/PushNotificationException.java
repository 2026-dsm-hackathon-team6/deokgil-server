package org.example.deokgilserver.common.push;

// 푸시 발송은 사용자 요청이 아니라 백그라운드 스케줄러에서 일어나므로, HTTP 응답으로 이어지는
// BusinessException/ErrorCode 대신 별도의 unchecked 예외로 실패를 알린다. 호출부(스케줄러)가
// 이 예외를 잡아 로그만 남기고 다음 알림으로 넘어갈 수 있게 하기 위함이다.
public class PushNotificationException extends RuntimeException {

    public PushNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
