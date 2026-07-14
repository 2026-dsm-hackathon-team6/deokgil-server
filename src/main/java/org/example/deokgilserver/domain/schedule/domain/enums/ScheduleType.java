package org.example.deokgilserver.domain.schedule.domain.enums;

// 행사 일정 종류
public enum ScheduleType {
    MOVE,        // 행사장 이동
    WAITING,     // 입장 대기
    GOODS,       // 굿즈 구매
    VISIT,       // 팝업스토어, 포토존 방문
    PERFORMANCE, // 공연, 팬미팅
    RETURN,      // 귀가
    ETC          // 기타 일정
}
