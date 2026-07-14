package org.example.deokgilserver.domain.event.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.domain.event.domain.enums.EventCreatedType;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.user.domain.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "events")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 등록한 사용자

    @Column(name = "title", nullable = false)
    private String title; // 행사명

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // 행사 시작 시간

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt; // 행사 종료 시간

    @Column(name = "place_name")
    private String placeName; // 행사장 이름

    @Column(name = "address")
    private String address; // 행사장 주소

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude; // 카카오 지도 위도

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude; // 카카오 지도 경도

    @Column(name = "event_url")
    private String eventUrl; // 공식 행사 URL

    @Enumerated(EnumType.STRING)
    @Column(name = "created_type")
    private EventCreatedType createdType; // 등록 방식 (MANUAL / AI)

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EventStatus status; // 행사 상태

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 삭제 시간

    @Builder
    public Event(User user, String title, LocalDateTime startAt, LocalDateTime endAt,
                 String placeName, String address, BigDecimal latitude, BigDecimal longitude,
                 String eventUrl, EventCreatedType createdType, EventStatus status) {
        this.user = user;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.placeName = placeName;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.eventUrl = eventUrl;
        this.createdType = createdType;
        this.status = status;
    }

    public void delete() {
        this.status = EventStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }
}
