package org.example.deokgilserver.domain.eventrecord.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.user.domain.User;

import java.util.UUID;

@Getter
@Entity
@Table(name = "event_records")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventRecord extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 사용자

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event; // 행사

    @Lob
    @Column(name = "review")
    private String review; // 행사 후기

    @Column(name = "photo_url")
    private String photoUrl; // 인증 사진 URL

    @Builder
    public EventRecord(User user, Event event, String review, String photoUrl) {
        this.user = user;
        this.event = event;
        this.review = review;
        this.photoUrl = photoUrl;
    }

    public void updateRecord(String review, String photoUrl) {
        this.review = review;
        this.photoUrl = photoUrl;
    }
}
