package org.example.deokgilserver.domain.event.presentation.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CreateEventRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void 날짜만_온_endAt은_해당_날짜_23시59분59초로_파싱된다() throws Exception {
        String json = """
                {
                  "title": "[수원] 2026 씨야 20주년 전국 투어 콘서트 : THE FAN",
                  "startAt": "2026-10-17T14:04:00.000Z",
                  "endAt": "2026-10-17",
                  "placeName": "경희대학교 국제캠퍼스 선승관",
                  "address": "경기 용인시 기흥구 덕영대로 1732 (서천동, 경희대학교국제캠퍼스)",
                  "eventUrl": "https://ticket.yes24.com/Perf/59236"
                }
                """;

        CreateEventRequest request = objectMapper.readValue(json, CreateEventRequest.class);

        assertThat(request.startAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 14, 4, 0));
        assertThat(request.endAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 23, 59, 59));
        assertThat(request.isEndAtAfterStartAt()).isTrue();
    }

    @Test
    void 날짜만_온_startAt은_해당_날짜_자정으로_파싱된다() throws Exception {
        String json = """
                {
                  "title": "테스트 행사",
                  "startAt": "2026-10-17",
                  "endAt": "2026-10-17T23:00:00",
                  "placeName": null,
                  "address": null,
                  "eventUrl": null
                }
                """;

        CreateEventRequest request = objectMapper.readValue(json, CreateEventRequest.class);

        assertThat(request.startAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 0, 0, 0));
        assertThat(request.endAt()).isEqualTo(LocalDateTime.of(2026, 10, 17, 23, 0, 0));
    }
}
