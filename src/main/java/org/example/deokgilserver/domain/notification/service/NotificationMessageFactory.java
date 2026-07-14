package org.example.deokgilserver.domain.notification.service;

import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.notification.domain.Notification;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 알림 종류별 제목/내용 문구를 만든다. 실제 푸시 발송(NotificationServiceImpl.dispatchOne)과
 * 알림 목록 조회 API(NotificationResponse) 양쪽에서 같은 문구를 써야 해서 별도로 뺐다.
 */
public final class NotificationMessageFactory {

    private NotificationMessageFactory() {
    }

    public static String titleFor(Notification notification) {
        return switch (notification.getType()) {
            case DAY_BEFORE -> "내일은 " + notification.getEvent().getTitle() + " 가는 날이에요";
            case DEPARTURE -> "출발할 시간이에요";
            case PERFORMANCE_START -> "곧 시작해요";
            case RETURN -> "행사가 끝났어요";
        };
    }

    public static String bodyFor(Notification notification) {
        Event event = notification.getEvent();
        return switch (notification.getType()) {
            case DAY_BEFORE -> "내일 " + formatTime(event.getStartAt()) + "에 " + safePlace(event) + "에서 시작해요. 준비물을 미리 확인해보세요.";
            case DEPARTURE -> safePlace(event) + "까지 이동할 시간이에요. 늦지 않게 출발하세요!";
            case PERFORMANCE_START -> event.getTitle() + "이(가) 곧 시작합니다.";
            case RETURN -> "오늘 하루도 고생 많으셨어요. 안전하게 귀가하세요.";
        };
    }

    private static String safePlace(Event event) {
        return StringUtils.hasText(event.getPlaceName()) ? event.getPlaceName() : "행사장";
    }

    private static String formatTime(LocalDateTime time) {
        return "%02d:%02d".formatted(time.getHour(), time.getMinute());
    }
}
