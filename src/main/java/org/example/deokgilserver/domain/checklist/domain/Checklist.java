package org.example.deokgilserver.domain.checklist.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.domain.event.domain.Event;

import java.util.UUID;

@Getter
@Entity
@Table(name = "checklists")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Checklist extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event; // 행사

    @Column(name = "content", nullable = false)
    private String content; // 준비물 내용

    @Column(name = "checked")
    private Boolean checked = false; // 체크 완료 여부

    @Builder
    public Checklist(Event event, String content, Boolean checked) {
        this.event = event;
        this.content = content;
        this.checked = (checked != null) ? checked : false;
    }

    public void toggle() {
        this.checked = !this.checked;
    }
}
