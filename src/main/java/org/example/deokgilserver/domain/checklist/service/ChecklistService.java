package org.example.deokgilserver.domain.checklist.service;

import org.example.deokgilserver.domain.checklist.presentation.dto.response.ChecklistResponse;

import java.util.UUID;

public interface ChecklistService {

    ChecklistResponse generateChecklist(UUID userId, UUID eventId);
}
