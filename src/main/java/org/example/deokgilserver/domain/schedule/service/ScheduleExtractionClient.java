package org.example.deokgilserver.domain.schedule.service;

import org.example.deokgilserver.domain.event.domain.Event;
import org.example.deokgilserver.domain.schedule.presentation.dto.request.GenerateScheduleRequest;

import java.util.List;

public interface ScheduleExtractionClient {

    List<GeneratedSchedule> generate(Event event, GenerateScheduleRequest request);
}
