package org.example.deokgilserver.domain.schedule.repository;

import org.example.deokgilserver.domain.schedule.domain.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    // 특정 행사의 일정을 시작 시간 순으로 조회
    List<Schedule> findByEventIdOrderByStartAtAsc(UUID eventId);

    // 특정 행사의 일정 전체 삭제 (행사 삭제 시 사용)
    void deleteByEventId(UUID eventId);
}
