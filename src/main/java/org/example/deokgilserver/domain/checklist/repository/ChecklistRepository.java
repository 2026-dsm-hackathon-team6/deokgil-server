package org.example.deokgilserver.domain.checklist.repository;

import org.example.deokgilserver.domain.checklist.domain.Checklist;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ChecklistRepository extends JpaRepository<Checklist, UUID> {

    // 특정 행사의 체크리스트 조회
    List<Checklist> findByEventId(UUID eventId);

    // 특정 행사의 체크리스트 전체 삭제 (행사 삭제 시 사용)
    void deleteByEventId(UUID eventId);
}
