package org.example.deokgilserver.common.weather;

import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

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
}
