package org.example.deokgilserver.common.weather;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 기상청 단기예보 조회서비스(getVilageFcst)를 사용한다.
 * https://www.data.go.kr 공공데이터포털에서 발급받은 서비스키가 필요하다.
 *
 * OpenWeatherMap과 달리 좌표계가 위경도가 아니라 기상청 자체 격자(nx, ny)라서,
 * 위경도 -> 격자 변환(Lambert Conformal Conic 투영)을 직접 계산해야 한다. 아래 toGrid()는
 * 기상청이 배포하는 C 예제 코드(단기예보 Open API 활용가이드 참고자료)를 그대로 Java로 옮긴 것이다 —
 * 계수를 임의로 바꾸면 좌표가 어긋나므로 원본 상수를 그대로 유지해야 한다.
 */
@Slf4j
@Component
public class KmaWeatherClient implements WeatherClient {

    private static final String FORECAST_URI = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

    // 단기예보는 하루 8회(02,05,08,11,14,17,20,23시) 발표되고, 각 발표 시각의 자료는
    // 발표 후 10분 뒤부터 조회 가능하다 — 그 전이면 그 이전 발표를 써야 한다.
    private static final int[] BASE_HOURS_DESC = {23, 20, 17, 14, 11, 8, 5, 2};
    private static final int PUBLISH_DELAY_MINUTES = 10;

    // 기상청 격자 변환 상수 (Lambert Conformal Conic). 임의 조정 금지.
    private static final double EARTH_RADIUS_KM = 6371.00877;
    private static final double GRID_KM = 5.0;
    private static final double STANDARD_LAT1 = 30.0;
    private static final double STANDARD_LAT2 = 60.0;
    private static final double ORIGIN_LON = 126.0;
    private static final double ORIGIN_LAT = 38.0;
    private static final double ORIGIN_X = 210.0 / GRID_KM;
    private static final double ORIGIN_Y = 675.0 / GRID_KM;

    private final WebClient webClient;
    private final String serviceKey;

    public KmaWeatherClient(
            WebClient.Builder webClientBuilder,
            @Value("${weather.api.key}") String serviceKey
    ) {
        this.webClient = webClientBuilder.build();
        this.serviceKey = serviceKey;
    }

    @Override
    public WeatherCondition getForecast(Coordinate coordinate, LocalDateTime targetTime) {
        Grid grid = toGrid(coordinate.longitude().doubleValue(), coordinate.latitude().doubleValue());
        LocalDateTime baseDateTime = resolveLatestBaseDateTime();
        URI uri = buildUri(grid, baseDateTime);

        try {
            VilageFcstResponse response = webClient.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(VilageFcstResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            List<Item> items = response == null || response.response() == null
                    || response.response().body() == null || response.response().body().items() == null
                    ? null
                    : response.response().body().items().item();

            if (items == null || items.isEmpty()) {
                throw new BusinessException(ErrorCode.WEATHER_API_ERROR);
            }

            return resolveCondition(items, targetTime);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // serviceKey는 URL 쿼리 파라미터에 실려 있다. WebClientResponseException 등은
            // 예외 메시지에 요청 URI를 그대로 포함시키는 경우가 있어, 메시지 대신 예외 종류만 남긴다.
            log.warn("기상청 날씨 조회 실패: {}", e.getClass().getSimpleName());
            throw new BusinessException(ErrorCode.WEATHER_API_ERROR);
        }
    }

    private URI buildUri(Grid grid, LocalDateTime baseDateTime) {
        // serviceKey는 공공데이터포털에서 발급 시점에 이미 URL 인코딩된 값으로 내려온다.
        // UriComponentsBuilder에게 다시 인코딩시키면(이중 인코딩) 서명이 깨져 인증에 실패하므로,
        // build(true)로 "이미 인코딩된 값"임을 명시하고 나머지 파라미터는 그대로 이어붙인다
        // (숫자/날짜만 있어 별도 인코딩이 필요 없다).
        return UriComponentsBuilder
                .fromUriString(FORECAST_URI + "?serviceKey=" + serviceKey)
                .queryParam("numOfRows", 1000)
                .queryParam("pageNo", 1)
                .queryParam("dataType", "JSON")
                .queryParam("base_date", baseDateTime.format(DATE_FORMAT))
                .queryParam("base_time", baseDateTime.format(TIME_FORMAT))
                .queryParam("nx", grid.nx())
                .queryParam("ny", grid.ny())
                .build(true)
                .toUri();
    }

    private LocalDateTime resolveLatestBaseDateTime() {
        LocalDateTime reference = LocalDateTime.now().minusMinutes(PUBLISH_DELAY_MINUTES);
        for (int hour : BASE_HOURS_DESC) {
            if (reference.getHour() >= hour) {
                return reference.withHour(hour).withMinute(0).withSecond(0).withNano(0);
            }
        }
        // 자정 이후 02시 발표 전이면 전날 23시 발표를 사용한다.
        return reference.minusDays(1).withHour(23).withMinute(0).withSecond(0).withNano(0);
    }

    // 응답에는 발표 시점 이후 여러 날짜/시각의 예보가 한꺼번에 들어있다. targetTime과 가장
    // 가까운 예보 시각을 찾고, 그 시각의 PTY(강수형태)/SKY(하늘상태) 값으로 날씨를 판단한다.
    private WeatherCondition resolveCondition(List<Item> items, LocalDateTime targetTime) {
        Map<LocalDateTime, Map<String, String>> byFcstTime = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.fcstDate() == null || item.fcstTime() == null || item.category() == null) {
                continue;
            }
            LocalDateTime fcstAt = LocalDateTime.parse(
                    item.fcstDate() + item.fcstTime(), DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
            byFcstTime.computeIfAbsent(fcstAt, k -> new LinkedHashMap<>()).put(item.category(), item.fcstValue());
        }

        if (byFcstTime.isEmpty()) {
            throw new BusinessException(ErrorCode.WEATHER_API_ERROR);
        }

        LocalDateTime nearest = byFcstTime.keySet().stream()
                .min(Comparator.comparingLong(t -> Math.abs(java.time.Duration.between(t, targetTime).toMinutes())))
                .orElseThrow(() -> new BusinessException(ErrorCode.WEATHER_API_ERROR));

        Map<String, String> categories = byFcstTime.get(nearest);
        return WeatherCondition.fromKmaCodes(categories.get("PTY"), categories.get("SKY"));
    }

    /**
     * 위경도 -> 기상청 격자좌표 변환 (Lambert Conformal Conic Projection).
     * 원본: 기상청 단기예보 조회서비스 Open API 활용가이드의 C 예제(lamcproj)를 그대로 이식.
     */
    // 격자 변환 공식 자체를 단위 테스트에서 직접 검증할 수 있도록 package-private으로 둔다.
    Grid toGrid(double lon, double lat) {
        double degrad = Math.PI / 180.0;
        double re = EARTH_RADIUS_KM / GRID_KM;
        double slat1 = STANDARD_LAT1 * degrad;
        double slat2 = STANDARD_LAT2 * degrad;
        double olon = ORIGIN_LON * degrad;
        double olat = ORIGIN_LAT * degrad;

        double sn = Math.tan(Math.PI * 0.25 + slat2 * 0.5) / Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sn = Math.log(Math.cos(slat1) / Math.cos(slat2)) / Math.log(sn);
        double sf = Math.tan(Math.PI * 0.25 + slat1 * 0.5);
        sf = Math.pow(sf, sn) * Math.cos(slat1) / sn;
        double ro = Math.tan(Math.PI * 0.25 + olat * 0.5);
        ro = re * sf / Math.pow(ro, sn);

        double ra = Math.tan(Math.PI * 0.25 + lat * degrad * 0.5);
        ra = re * sf / Math.pow(ra, sn);
        double theta = lon * degrad - olon;
        if (theta > Math.PI) {
            theta -= 2.0 * Math.PI;
        }
        if (theta < -Math.PI) {
            theta += 2.0 * Math.PI;
        }
        theta *= sn;

        int nx = (int) (ra * Math.sin(theta) + ORIGIN_X + 1.5);
        int ny = (int) (ro - ra * Math.cos(theta) + ORIGIN_Y + 1.5);
        return new Grid(nx, ny);
    }

    record Grid(int nx, int ny) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record VilageFcstResponse(Response response) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Response(Body body) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Body(Items items) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Items(List<Item> item) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Item(String category, String fcstDate, String fcstTime, String fcstValue) {
    }
}
