package org.example.deokgilserver.common.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;

/**
 * 카카오 로컬 API의 키워드 장소 검색을 사용해, 좌표 주변에서 이름으로 장소를 찾는다.
 * https://developers.kakao.com/docs/latest/ko/local/dev-guide#search-by-keyword
 */
@Slf4j
@Component
public class KakaoPlaceSearchClient implements PlaceSearchClient {

    private static final String SEARCH_URI = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String restApiKey;

    public KakaoPlaceSearchClient(
            WebClient.Builder webClientBuilder,
            @Value("${kakao.api.key}") String restApiKey
    ) {
        this.webClient = webClientBuilder.build();
        this.restApiKey = restApiKey;
    }

    @Override
    public PlaceResult search(String keyword, Coordinate near, int radiusMeters) {
        URI uri = UriComponentsBuilder.fromUriString(SEARCH_URI)
                .queryParam("query", keyword)
                .queryParam("x", near.longitude())
                .queryParam("y", near.latitude())
                .queryParam("radius", radiusMeters)
                .queryParam("sort", "distance")
                .encode()
                .build()
                .toUri();

        try {
            // API 키는 Authorization 헤더로만 전달한다 — 쿼리 파라미터에 넣지 않으므로 실패 시
            // 예외 메시지를 로그에 남겨도 키가 노출되지 않는다(KakaoGeocodingClient와 동일).
            KakaoKeywordResponse response = webClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .bodyToMono(KakaoKeywordResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                throw new BusinessException(ErrorCode.INVALID_LOCATION);
            }

            KakaoPlace place = response.documents().get(0);
            Coordinate coordinate = new Coordinate(new BigDecimal(place.y()), new BigDecimal(place.x()));
            return new PlaceResult(place.placeName(), coordinate);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("카카오 장소 검색 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.MAP_API_ERROR);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoKeywordResponse(List<KakaoPlace> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoPlace(String x, String y, @JsonProperty("place_name") String placeName) {
    }
}
