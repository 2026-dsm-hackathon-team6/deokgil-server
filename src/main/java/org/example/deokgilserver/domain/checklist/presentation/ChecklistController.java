package org.example.deokgilserver.domain.checklist.presentation;

import org.example.deokgilserver.domain.checklist.presentation.dto.response.ChecklistResponse;
import org.example.deokgilserver.domain.checklist.service.ChecklistService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events/{eventId}/checklist")
public class ChecklistController {

    private final ChecklistService checklistService;

    public ChecklistController(ChecklistService checklistService) {
        this.checklistService = checklistService;
    }

    // eventType/weather를 요청 바디로 받지 않는다 — 행사 위치와 시각을 기준으로 서버가
    // 직접 좌표 변환 및 날씨 조회를 수행해 추천 결과를 생성한다.
    @PostMapping
    public ChecklistResponse generateChecklist(@AuthenticationPrincipal UUID userId, @PathVariable UUID eventId) {
        return checklistService.generateChecklist(userId, eventId);
    }
}
