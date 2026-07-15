package org.example.deokgilserver.common.json;

import java.time.LocalDate;
import java.time.LocalDateTime;

// 날짜만 온 시작 시각은 그 날의 00:00:00으로 취급한다.
public class StartOfDayLocalDateTimeDeserializer extends FlexibleLocalDateTimeDeserializer {

    @Override
    protected LocalDateTime onDateOnly(LocalDate date) {
        return date.atStartOfDay();
    }
}
