package org.example.deokgilserver.common.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * 프론트에서 시간 정보 없이 날짜만("yyyy-MM-dd") 보내는 경우까지 받아주기 위한 파서.
 * 기본 Jackson LocalDateTime 역직렬화는 "yyyy-MM-dd'T'HH:mm:ss" 형식만 허용해서,
 * 날짜만 오면 요청 자체가 HttpMessageNotReadableException으로 400 처리된다.
 * 날짜만 온 경우 시각을 어떻게 채울지는 하위 클래스가 결정한다.
 */
public abstract class FlexibleLocalDateTimeDeserializer extends StdDeserializer<LocalDateTime> {

    protected FlexibleLocalDateTimeDeserializer() {
        super(LocalDateTime.class);
    }

    protected abstract LocalDateTime onDateOnly(LocalDate date);

    @Override
    public LocalDateTime deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        String value = p.getText();
        if (value == null || value.isBlank()) {
            return null;
        }
        value = value.trim();

        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException ignored) {
            // "yyyy-MM-dd'T'HH:mm:ss(.SSS)?Z" 같이 오프셋/타임존이 붙은 경우
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            // 시간 정보가 아예 없는 "yyyy-MM-dd" 형식일 수 있음
        }
        try {
            return onDateOnly(LocalDate.parse(value));
        } catch (DateTimeParseException e) {
            throw new IOException("날짜/시간 형식이 올바르지 않습니다: " + value, e);
        }
    }
}
