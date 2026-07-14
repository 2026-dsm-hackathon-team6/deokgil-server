package org.example.deokgilserver.domain.location.service;

import org.example.deokgilserver.domain.location.presentation.dto.request.LocationObfuscationRequest;
import org.example.deokgilserver.domain.location.presentation.dto.response.LocationObfuscationResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 좌표를 소수점 3자리(약 111m 오차)로 반올림해 정밀도를 낮춘다. EventLocationResolver가
 * 이벤트 저장 시점에 4자리(~11m)로 이미 한 번 낮추는 것과는 별개로, 이 엔드포인트는 클라이언트가
 * 임의의 좌표를 그때그때 즉석에서 뭉개고 싶을 때 쓰는 범용 유틸리티다(저장하지 않고 바로 반환).
 */
@Component
public class LocationObfuscator {

    private static final int DECIMAL_PLACES = 3;

    public LocationObfuscationResponse obfuscate(LocationObfuscationRequest request) {
        double obfuscatedLatitude = roundCoordinate(request.latitude());
        double obfuscatedLongitude = roundCoordinate(request.longitude());

        return new LocationObfuscationResponse(obfuscatedLatitude, obfuscatedLongitude);
    }

    private double roundCoordinate(double coordinate) {
        return BigDecimal.valueOf(coordinate)
                .setScale(DECIMAL_PLACES, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
