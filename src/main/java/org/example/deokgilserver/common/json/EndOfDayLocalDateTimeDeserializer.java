package org.example.deokgilserver.common.json;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

// 날짜만 온 종료 시각은 그 날의 23:59:59로 취급해서 하루 전체를 포함시킨다.
public class EndOfDayLocalDateTimeDeserializer extends FlexibleLocalDateTimeDeserializer {

    @Override
    protected LocalDateTime onDateOnly(LocalDate date) {
        return date.atTime(LocalTime.of(23, 59, 59));
    }
}
