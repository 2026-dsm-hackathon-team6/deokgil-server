package org.example.deokgilserver.common.push;

// 푸시 발송은 사용자 요청이 아니라 백그라운드 스케줄러에서 일어나므로, HTTP 응답으로 이어지는
// BusinessException/ErrorCode 대신 별도의 unchecked 예외로 실패를 알린다. 호출부(스케줄러)가
// 이 예외를 잡아 로그만 남기고 다음 알림으로 넘어갈 수 있게 하기 위함이다.
public class PushNotificationException extends RuntimeException {

    // 토큰이 만료/폐기(UNREGISTERED)되었거나 형식 자체가 잘못돼(INVALID_ARGUMENT) 이 토큰으로는
    // 재시도해도 절대 성공할 수 없는 경우를 구분한다 - 호출부가 이 값을 보고 User.fcmToken을
    // 지워서, 이미 죽은 토큰으로 매 스케줄 주기마다 헛되이 재시도하는 것을 막을 수 있다.
    private final boolean invalidToken;

    public PushNotificationException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public PushNotificationException(String message, Throwable cause, boolean invalidToken) {
        super(message, cause);
        this.invalidToken = invalidToken;
    }

    public boolean isInvalidToken() {
        return invalidToken;
    }
}
