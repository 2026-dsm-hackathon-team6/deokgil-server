package org.example.deokgilserver.common.push;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FirebasePushNotificationClient implements PushNotificationClient {

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
            // 토큰이 만료/폐기된 경우도 여기 포함된다 — 지금은 재시도만 하고 넘어가지만,
            // 향후 e.getMessagingErrorCode()가 UNREGISTERED일 때 User.fcmToken을 지우는
            // 정리 로직을 추가하면 더 정확하다.
            log.warn("FCM 발송 실패: {}", e.getMessagingErrorCode());
            throw new PushNotificationException("푸시 알림 발송에 실패했습니다.", e);
        }
    }
}
