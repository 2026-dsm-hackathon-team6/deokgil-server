package org.example.deokgilserver.domain.schedule.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.common.security.EncryptedScheduleLatitudeConverter;
import org.example.deokgilserver.common.security.EncryptedScheduleLongitudeConverter;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus;
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

    // 현재 코드 경로상 이 필드들은 아직 채워지는 곳이 없지만(AI 일정 생성이 좌표까지는
    // 채우지 않음), Event.latitude/longitude와 같은 성격의 데이터라 같은 방식으로
    // 암호화해둔다 — 나중에 일정 단위 좌표를 쓰게 되어도 별도 마이그레이션이 필요 없도록.
    @Convert(converter = EncryptedScheduleLatitudeConverter.class)
    @Column(name = "latitude")
    private BigDecimal latitude; // 일정 장소 위도 (암호화 저장)

    @Convert(converter = EncryptedScheduleLongitudeConverter.class)
    @Column(name = "longitude")
    private BigDecimal longitude; // 일정 장소 경도 (암호화 저장)

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt; // 일정 시작 시간

    @Column(name = "end_at")
    private LocalDateTime endAt; // 일정 종료 시간

    @Column(name = "is_ai")
    private Boolean isAi = true; // AI 생성 여부

    @Column(name = "is_modified")
    private Boolean isModified = false; // 사용자가 수정했는지 여부

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScheduleStatus status = ScheduleStatus.ACTIVE; // 일정 상태 (Soft Delete)

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 삭제 시간

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
        this.status = ScheduleStatus.ACTIVE;
    }

    public void modify() {
        this.isModified = true;
    }

    // 부분 수정: null인 필드는 기존 값을 유지한다(PATCH 의미론).
    public void update(String title, LocalDateTime startAt, LocalDateTime endAt) {
        if (title != null) {
            this.title = title;
        }
        if (startAt != null) {
            this.startAt = startAt;
        }
        if (endAt != null) {
            this.endAt = endAt;
        }
        modify();
    }

    public void delete() {
        this.status = ScheduleStatus.DELETED;
        this.deletedAt = LocalDateTime.now();
    }
}
