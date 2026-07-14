package org.example.deokgilserver.domain.event.service;

import lombok.extern.slf4j.Slf4j;
import org.example.deokgilserver.domain.event.repository.EventRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 위치 최소 수집 원칙의 "보관 기간 제한" 부분을 담당한다: 행사장 좌표는 그 행사가 진행되는
 * 동안(체크리스트/동선/브리핑/지도 기능)만 필요하고, 행사가 끝나고 나면 더 이상 쓸 곳이 없다.
 * 그런데도 계속 들고 있으면 암호화를 했더라도 "불필요하게 오래 보관된 정밀 위치 데이터"라는
 * 위험이 남으므로, 일정 기간(RETENTION_DAYS)이 지나면 좌표만 비운다 — 행사 기록(title/date/
 * placeName)은 남겨서 EventService.getEventHistory가 계속 정상 동작하게 한다.
 */
@Slf4j
@Component
public class LocationRetentionScheduler {

    private static final int RETENTION_DAYS = 30;

    private final EventRepository eventRepository;

    public LocationRetentionScheduler(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // 매일 새벽 4시(트래픽이 적을 시간대)에 한 번 실행한다.
    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void clearExpiredCoordinates() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(RETENTION_DAYS);
        int cleared = eventRepository.clearCoordinatesForEventsEndedBefore(cutoff);
        if (cleared > 0) {
            log.info("행사 종료 {}일 경과로 좌표 삭제된 행사 수: {}", RETENTION_DAYS, cleared);
        }
    }
}
