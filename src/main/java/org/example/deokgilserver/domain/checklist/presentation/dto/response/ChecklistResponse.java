package org.example.deokgilserver.domain.checklist.presentation.dto.response;

import org.example.deokgilserver.domain.checklist.domain.Checklist;

import java.util.List;
import java.util.UUID;

public record ChecklistResponse(UUID eventId, String weather, List<ChecklistItemResponse> items) {

    public static ChecklistResponse of(UUID eventId, String weather, List<Checklist> checklists) {
        return new ChecklistResponse(
                eventId,
                weather,
                checklists.stream().map(ChecklistItemResponse::from).toList()
        );
    }
}
