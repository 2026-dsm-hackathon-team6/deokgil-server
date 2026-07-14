package org.example.deokgilserver.domain.schedule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "schedules")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Schedule extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event; // 행사

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private ScheduleType type; // 일정 종류

    @Column(name = "title", nullable = false)
    private String title; // 일정 제목

    @Lob
    @Column(name = "description")
    private String description; // 일정 상세 설명

    @Column(name = "place_name")
    private String placeName; // 일정 장소

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude; // 일정 장소 위도

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude; // 일정 장소 경도

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // 일정 시작 시간

    @Column(name = "end_at")
    private LocalDateTime endAt; // 일정 종료 시간

    @Column(name = "is_ai")
    private Boolean isAi = true; // AI 생성 여부

    @Column(name = "is_modified")
    private Boolean isModified = false; // 사용자가 수정했는지 여부

    @Builder
    public Schedule(Event event, ScheduleType type, String title, String description,
                     String placeName, BigDecimal latitude, BigDecimal longitude,
                     LocalDateTime startAt, LocalDateTime endAt, Boolean isAi, Boolean isModified) {
        this.event = event;
        this.type = type;
        this.title = title;
        this.description = description;
        this.placeName = placeName;
        this.latitude = latitude;
        this.longitude = longitude;
        this.startAt = startAt;
        this.endAt = endAt;
        this.isAi = (isAi != null) ? isAi : true;
        this.isModified = (isModified != null) ? isModified : false;
    }

    public void modify() {
        this.isModified = true;
    }
}
