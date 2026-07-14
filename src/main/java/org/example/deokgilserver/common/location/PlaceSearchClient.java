package org.example.deokgilserver.common.location;

public interface PlaceSearchClient {

    // near 좌표 주변 radiusMeters 이내에서 keyword로 가장 관련도 높은 장소를 찾는다.
    PlaceResult search(String keyword, Coordinate near, int radiusMeters);
}
