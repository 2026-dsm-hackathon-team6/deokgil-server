package org.example.deokgilserver.common.weather;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class KmaWeatherClientTest {

    // 기상청 Open API 활용가이드에 실린 검증 예제:
    // lon=126.929810, lat=37.488201 --> X=59, Y=125
    @Test
    void 위경도를_기상청_격자좌표로_변환한다() {
        KmaWeatherClient client = new KmaWeatherClient(WebClient.builder(), "dummy-key");

        KmaWeatherClient.Grid grid = client.toGrid(126.929810, 37.488201);

        assertThat(grid.nx()).isEqualTo(59);
        assertThat(grid.ny()).isEqualTo(125);
    }

    @Test
    void 서울시청_좌표는_알려진_격자값으로_변환된다() {
        // 서울 종로구 서울시청 부근: 기상청 공식 자료 기준 격자 60,127
        KmaWeatherClient client = new KmaWeatherClient(WebClient.builder(), "dummy-key");

        KmaWeatherClient.Grid grid = client.toGrid(126.9779692, 37.566535);

        assertThat(grid.nx()).isEqualTo(60);
        assertThat(grid.ny()).isEqualTo(127);
    }

    @Test
    void 예보_제공_범위를_벗어난_시각은_예외_없이_UNKNOWN을_반환한다() {
        KmaWeatherClient client = new KmaWeatherClient(WebClient.builder(), "dummy-key");
        List<KmaWeatherClient.Item> items = List.of(
                new KmaWeatherClient.Item("PTY", "20260716", "0500", "0"),
                new KmaWeatherClient.Item("SKY", "20260716", "0500", "1"),
                new KmaWeatherClient.Item("PTY", "20260718", "2300", "0"),
                new KmaWeatherClient.Item("SKY", "20260718", "2300", "1")
        );
        LocalDateTime farFutureEvent = LocalDateTime.of(2026, 12, 25, 19, 0);

        WeatherCondition condition = client.resolveCondition(items, farFutureEvent);

        assertThat(condition).isEqualTo(WeatherCondition.UNKNOWN);
    }

    @Test
    void 예보_제공_범위_안이면_가장_가까운_시각의_날씨를_반환한다() {
        KmaWeatherClient client = new KmaWeatherClient(WebClient.builder(), "dummy-key");
        List<KmaWeatherClient.Item> items = List.of(
                new KmaWeatherClient.Item("PTY", "20260716", "0500", "1"),
                new KmaWeatherClient.Item("SKY", "20260716", "0500", "4"),
                new KmaWeatherClient.Item("PTY", "20260716", "0600", "0"),
                new KmaWeatherClient.Item("SKY", "20260716", "0600", "1")
        );
        LocalDateTime withinRange = LocalDateTime.of(2026, 7, 16, 5, 10);

        WeatherCondition condition = client.resolveCondition(items, withinRange);

        assertThat(condition).isEqualTo(WeatherCondition.RAIN);
    }
}
