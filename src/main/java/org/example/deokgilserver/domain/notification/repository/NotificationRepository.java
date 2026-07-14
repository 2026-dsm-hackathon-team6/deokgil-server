package org.example.deokgilserver.domain.notification.repository;

import org.example.deokgilserver.domain.notification.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    // 특정 행사의 활성화된 알림 조회
    List<Notification> findByEventIdAndEnabledTrue(UUID eventId);

    // 특정 사용자의 알림을 최신순(예정 시각 내림차순)으로 조회 (알림 목록 API)
    List<Notification> findByEvent_User_IdOrderByNotifyAtDesc(UUID userId);

    // 발송 대상 알림 조회 (알림 시각이 지났고, 아직 발송되지 않았고, 활성화된 건) - 배치 발송용
    List<Notification> findByNotifyAtBeforeAndSentAtIsNullAndEnabledTrue(LocalDateTime now);

    // 특정 행사의 알림 전체 삭제 (행사 삭제 시 사용)
    void deleteByEventId(UUID eventId);
}
