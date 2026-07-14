package org.example.deokgilserver.common.location;

public final class GeoMath {

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    private GeoMath() {
    }

    // 두 좌표 사이의 직선 거리(m). 실제 도로/보행로 경로가 아닌 근사치이므로,
    // 이동 시간 추정에는 쓸 수 있어도 정밀한 내비게이션 용도로는 쓰지 않는다.
    public static double haversineMeters(Coordinate from, Coordinate to) {
        double lat1 = Math.toRadians(from.latitude().doubleValue());
        double lat2 = Math.toRadians(to.latitude().doubleValue());
        double deltaLat = Math.toRadians(to.latitude().doubleValue() - from.latitude().doubleValue());
        double deltaLon = Math.toRadians(to.longitude().doubleValue() - from.longitude().doubleValue());

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_METERS * c;
    }
}
