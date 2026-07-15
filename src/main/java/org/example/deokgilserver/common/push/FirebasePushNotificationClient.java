package org.example.deokgilserver.common.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class FirebasePushNotificationClient implements PushNotificationClient {

    // 이 토큰으로는 재시도해도 절대 성공하지 못하는 에러코드 - 앱 삭제/토큰 만료(UNREGISTERED),
    // 다른 프로젝트 발급 토큰(SENDER_ID_MISMATCH), 형식 자체가 잘못된 토큰(INVALID_ARGUMENT).
    // QUOTA_EXCEEDED/UNAVAILABLE/INTERNAL 등은 토큰과 무관한 일시적 실패라 여기 포함하지 않는다 -
    // 그런 경우까지 토큰을 지우면, 멀쩡한 토큰을 재등록 전까지 알림을 못 받게 만드는 셈이다.
    private static final Set<MessagingErrorCode> INVALID_TOKEN_ERROR_CODES = Set.of(
            MessagingErrorCode.UNREGISTERED,
            MessagingErrorCode.SENDER_ID_MISMATCH,
            MessagingErrorCode.INVALID_ARGUMENT
    );

    private final FirebaseApp firebaseApp;

    public FirebasePushNotificationClient(FirebaseApp firebaseApp) {
        this.firebaseApp = firebaseApp;
    }

    @Override
    public void send(String deviceToken, String title, String body) {
        // com.google.firebase.messaging.Notification과 우리 도메인의 Notification 엔티티가
        // 이름이 같아 혼동되지 않도록 여기서만 전체 경로로 명시한다.
        com.google.firebase.messaging.Notification payload = com.google.firebase.messaging.Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        // setToken()이 최신 SDK에서 deprecated 표시되고 Firebase Installation ID(FID) 기반의
        // setFid()가 대체로 제시되지만, 우리 클라이언트(FCM 웹/모바일 SDK)는 여전히 등록
        // 토큰(registration token)을 발급받는 표준 방식이고 FID 마이그레이션 경로는 아직
        // 문서가 충분치 않다. 등록 토큰 기반 발송은 계속 정상 동작하므로 지금은 유지한다.
        @SuppressWarnings("deprecation")
        Message message = Message.builder()
                .setToken(deviceToken)
                .setNotification(payload)
                .build();

        try {
            FirebaseMessaging.getInstance(firebaseApp).send(message);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 발송 실패: {}", e.getMessagingErrorCode());
            boolean invalidToken = INVALID_TOKEN_ERROR_CODES.contains(e.getMessagingErrorCode());
            throw new PushNotificationException("푸시 알림 발송에 실패했습니다.", e, invalidToken);
        }
    }
}
