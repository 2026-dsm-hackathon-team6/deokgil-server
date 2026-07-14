package org.example.deokgilserver.domain.user.repository;

import org.example.deokgilserver.domain.user.domain.User;
import org.example.deokgilserver.domain.user.domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    // Google OAuth ID로 조회
    Optional<User> findByGoogleId(String googleId);

    // 이메일로 조회
    Optional<User> findByEmail(String email);

    // 이메일 중복 확인
    boolean existsByEmail(String email);

    // 상태(ACTIVE/WITHDRAW)로 조회
    Optional<User> findByIdAndStatus(UUID id, UserStatus status);
}
