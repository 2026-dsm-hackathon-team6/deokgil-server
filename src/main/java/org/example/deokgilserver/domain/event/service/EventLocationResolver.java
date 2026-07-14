package org.example.deokgilserver.domain.event.service;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.example.deokgilserver.common.location.Coordinate;
import org.example.deokgilserver.common.location.GeocodingClient;
import org.example.deokgilserver.domain.event.domain.Event;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 체크리스트/동선 안내 등 위치 기반 기능에서 공통으로 쓰는 좌표 확보 로직.
 * 이벤트에 좌표가 이미 있으면 그대로 쓰고, 없으면 주소를 1회 지오코딩해서 이벤트에 저장해 재사용한다.
 */
@Component
public class EventLocationResolver {

    // 위치 난독화: 소수점 4자리(약 11m 오차)로 반올림해서 저장한다. 기상청 격자(5km 단위),
    // 동선 추천의 이동거리 근사치, 반경 1~5km 장소 검색 어디에도 11m 오차는 결과에 영향을
    // 주지 않으면서, 건물 출입구 단위까지 특정할 수 있는 정밀도는 애초에 수집하지 않는다.
    private static final int COORDINATE_SCALE = 4;

    private final GeocodingClient geocodingClient;

    public EventLocationResolver(GeocodingClient geocodingClient) {
        this.geocodingClient = geocodingClient;
    }

    // 위치가 없을 때 던질 에러를 호출부가 고른다 — 같은 상황(주소도 좌표도 없음)이라도
    // 엔드포인트마다 스펙에 정의된 상태코드/메시지가 다르기 때문이다
    // (체크리스트/동선/브리핑은 400 EVENT_LOCATION_REQUIRED, 행사장 지도는 404 LOCATION_NOT_FOUND).
    public Coordinate resolve(Event event, ErrorCode missingLocationError) {
        if (event.getLatitude() != null && event.getLongitude() != null) {
            return new Coordinate(event.getLatitude(), event.getLongitude());
        }

        if (event.getAddress() == null || event.getAddress().isBlank()) {
            throw new BusinessException(missingLocationError);
        }

        Coordinate geocoded = geocodingClient.geocode(event.getAddress());
        Coordinate coordinate = obfuscate(geocoded);
        event.assignCoordinates(coordinate.latitude(), coordinate.longitude());
        return coordinate;
    }

    private Coordinate obfuscate(Coordinate coordinate) {
        return new Coordinate(
                coordinate.latitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP),
                coordinate.longitude().setScale(COORDINATE_SCALE, RoundingMode.HALF_UP)
        );
    }
}
