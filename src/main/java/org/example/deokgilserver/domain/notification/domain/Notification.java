package org.example.deokgilserver.domain.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.notification.domain.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "notifications")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event; // 행사

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type; // 알림 종류

    @Column(name = "notify_at", nullable = false)
    private LocalDateTime notifyAt; // 알림 예정 시간

    @Column(name = "enabled")
    private Boolean enabled = true; // 알림 활성화 여부

    @Column(name = "sent_at")
    private LocalDateTime sentAt; // 실제 발송 시간

    @Builder
    public Notification(Event event, NotificationType type, LocalDateTime notifyAt, Boolean enabled) {
        this.event = event;
        this.type = type;
        this.notifyAt = notifyAt;
        this.enabled = (enabled != null) ? enabled : true;
    }

    public void markSent() {
        this.sentAt = LocalDateTime.now();
    }

    public void disable() {
        this.enabled = false;
    }
}
