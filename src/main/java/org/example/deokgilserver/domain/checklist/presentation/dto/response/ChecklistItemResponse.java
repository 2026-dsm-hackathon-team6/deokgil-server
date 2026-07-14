package org.example.deokgilserver.domain.checklist.presentation.dto.response;

import org.example.deokgilserver.domain.checklist.domain.Checklist;

import java.util.UUID;

public record ChecklistItemResponse(UUID checklistId, String content, Boolean checked) {

    public static ChecklistItemResponse from(Checklist checklist) {
        return new ChecklistItemResponse(checklist.getId(), checklist.getContent(), checklist.getChecked());
    }
}
