package org.example.deokgilserver.domain.schedule.repository;

import org.example.deokgilserver.domain.schedule.domain.Schedule;
import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    // 특정 행사의 삭제되지 않은 일정을 시작 시간 순으로 조회
    List<Schedule> findByEventIdAndStatusOrderByStartAtAsc(UUID eventId, ScheduleStatus status);

    // 특정 행사에 삭제되지 않은 일정이 이미 있는지 확인 (AI 일정 재생성 방지)
    boolean existsByEventIdAndStatus(UUID eventId, ScheduleStatus status);

    // 행사 삭제 시 사용. 행사 자체가 Soft Delete이므로 일정도 하드 삭제 대신 같은 방식으로
    // 맞춰서, 개별 일정 삭제(Schedule.delete())와 이력 보존 여부가 갈리지 않게 한다.
    @Modifying
    @Query("update Schedule s set s.status = org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus.DELETED, "
            + "s.deletedAt = :deletedAt where s.event.id = :eventId and s.status = org.example.deokgilserver.domain.schedule.domain.enums.ScheduleStatus.ACTIVE")
    void softDeleteByEventId(@Param("eventId") UUID eventId, @Param("deletedAt") LocalDateTime deletedAt);
}
