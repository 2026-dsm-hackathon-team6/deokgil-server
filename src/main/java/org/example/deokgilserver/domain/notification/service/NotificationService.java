package org.example.deokgilserver.domain.notification.service;

import org.example.deokgilserver.domain.event.domain.Event;

public interface NotificationService {

    // 행사 등록 시점에 그 행사의 알림 4종(하루 전/출발/공연 시작/귀가)을 예약한다.
    void scheduleEventNotifications(Event event);

    // 발송 시각이 지난, 아직 안 보낸 알림들을 찾아 실제로 푸시를 보낸다.
    void dispatchDueNotifications();
}
