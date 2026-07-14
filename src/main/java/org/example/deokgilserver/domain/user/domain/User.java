package org.example.deokgilserver.domain.user.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.example.deokgilserver.common.BaseTimeEntity;
import org.example.deokgilserver.domain.user.domain.enums.UserRole;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "google_id", nullable = false, unique = true)
    private String googleId; // Google OAuth 사용자 ID

    @Column(name = "email", nullable = false, unique = true)
    private String email; // 사용자 이메일

    @Column(name = "nickname", nullable = false)
    private String nickname; // 사용자 별명

    @Column(name = "profile_image")
    private String profileImage; // 프로필 이미지 URL

    @Column(name = "fcm_token")
    private String fcmToken; // 푸시 알림용 FCM 디바이스 토큰

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role; // 사용자 권한

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UserStatus status; // 회원 상태

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt; // 탈퇴 시간 (Soft Delete)

    @Builder
    public User(String googleId, String email, String nickname, String profileImage,
                UserRole role, UserStatus status) {
        this.googleId = googleId;
        this.email = email;
        this.nickname = nickname;
        this.profileImage = profileImage;
        this.role = role;
        this.status = status;
    }

    public void withdraw() {
        this.status = UserStatus.WITHDRAW;
        this.deletedAt = LocalDateTime.now();
    }

    public void updateProfile(String nickname, String profileImage) {
        this.nickname = nickname;
        this.profileImage = profileImage;
    }

    public void updateFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
