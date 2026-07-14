package org.example.deokgilserver.domain.event.repository;

import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.event.domain.enums.EventStatus;
import org.example.deokgilserver.domain.user.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    // 특정 사용자가 등록한 행사 중 삭제되지 않은 목록 조회
    List<Event> findByUserAndStatus(User user, EventStatus status);

    // 삭제되지 않은 단건 조회 (Soft Delete 반영)
    Optional<Event> findByIdAndStatus(UUID id, EventStatus status);

    // 사용자 ID 기준 전체 행사 조회
    List<Event> findByUserId(UUID userId);

    // 상태값 기준 전체 조회 (예: 관리자 페이지)
    List<Event> findAllByStatus(EventStatus status);

    // 특정 사용자의 시작 전/진행 중 행사를 시작 시간 오름차순으로 페이징 조회
    Page<Event> findByUserIdAndStatusAndEndAtAfterOrderByStartAtAsc(
            UUID userId, EventStatus status, LocalDateTime now, Pageable pageable);
}
