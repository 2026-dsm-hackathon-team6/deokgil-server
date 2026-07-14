package org.example.deokgilserver.domain.schedule.service;

import org.example.deokgilserver.domain.schedule.domain.enums.ScheduleType;

import java.time.LocalDateTime;

public record GeneratedSchedule(ScheduleType type, String title, LocalDateTime startAt, LocalDateTime endAt) {
}
