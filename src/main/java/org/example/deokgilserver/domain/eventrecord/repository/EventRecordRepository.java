package org.example.deokgilserver.domain.eventrecord.repository;

import org.example.deokgilserver.domain.eventrecord.domain.EventRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRecordRepository extends JpaRepository<EventRecord, UUID> {

    // 특정 사용자의 행사 기록 전체 조회
    List<EventRecord> findByUserId(UUID userId);

    // 특정 행사의 기록 전체 조회
    List<EventRecord> findByEventId(UUID eventId);

    // 특정 사용자가 특정 행사에 남긴 기록 조회
    Optional<EventRecord> findByUserIdAndEventId(UUID userId, UUID eventId);
}
