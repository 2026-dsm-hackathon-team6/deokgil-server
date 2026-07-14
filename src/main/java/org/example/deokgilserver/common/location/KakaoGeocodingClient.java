package org.example.deokgilserver.common.location;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
 * 카카오 로컬 API의 주소 검색(주소 → 좌표 변환)을 사용한다.
 * https://developers.kakao.com/docs/latest/ko/local/dev-guide#address-coord
 */
@Slf4j
@Component
public class KakaoGeocodingClient implements GeocodingClient {

    private static final String GEOCODE_URI = "https://dapi.kakao.com/v2/local/search/address.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    private final WebClient webClient;
    private final String restApiKey;

    public KakaoGeocodingClient(
            WebClient.Builder webClientBuilder,
            @Value("${kakao.api.key}") String restApiKey
    ) {
        this.webClient = webClientBuilder.build();
        this.restApiKey = restApiKey;
    }

    @Override
    public Coordinate geocode(String address) {
        URI uri = UriComponentsBuilder.fromUriString(GEOCODE_URI)
                .queryParam("query", address)
                .encode()
                .build()
                .toUri();

        try {
            // API 키를 Authorization 헤더로 보낸다(URL 쿼리 파라미터가 아님) — 요청 URI 자체에는
            // 키가 없으므로, 실패 시 예외 메시지(보통 요청 URI를 포함)를 로그에 남겨도 키가
            // 새어나가지 않는다. KmaWeatherClient는 키를 쿼리 파라미터로만 지원해서 사정이 다르다.
            KakaoAddressResponse response = webClient.get()
                    .uri(uri)
                    .header("Authorization", "KakaoAK " + restApiKey)
                    .retrieve()
                    .bodyToMono(KakaoAddressResponse.class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            if (response == null || response.documents() == null || response.documents().isEmpty()) {
                throw new BusinessException(ErrorCode.GEOCODING_FAILED);
            }

            KakaoAddressDetail detail = response.documents().get(0).address();
            return new Coordinate(new BigDecimal(detail.y()), new BigDecimal(detail.x()));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("카카오 지오코딩 실패: {}", e.getMessage());
            throw new BusinessException(ErrorCode.GEOCODING_FAILED);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAddressResponse(List<KakaoDocument> documents) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoDocument(KakaoAddressDetail address) {
    }

    // 카카오 API는 x=경도(longitude), y=위도(latitude) 순서로 응답한다.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAddressDetail(String x, String y) {
    }
}
