package org.example.deokgilserver.domain.checklist.service;

import org.example.deokgilserver.domain.checklist.presentation.dto.response.ChecklistResponse;

import java.util.UUID;

public interface ChecklistService {

    ChecklistResponse generateChecklist(UUID userId, UUID eventId);

    // 내일 시작하는 행사 중 이미 체크리스트가 있는 것만 최신 날씨로 재생성한다.
    void regenerateChecklistsForTomorrowEvents();
}
