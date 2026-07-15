package org.example.deokgilserver.domain.event.presentation.dto.response;

// startAt/endAt은 ISO-8601 문자열이다 — 페이지에 정확한 시각까지 나와 있으면
// "yyyy-MM-dd'T'HH:mm:ss", 날짜만 확인 가능하면 "yyyy-MM-dd" 형식으로 내려온다.
// 이 응답은 실제 저장 전 미리보기이므로, 시각을 임의로 지어내지 않고 아는 만큼만
// 그대로 전달한다 - 시각 채움 여부는 프론트/사용자가 CreateEventRequest로 등록할 때 결정한다.
public record ExtractEventResponse(
        String title,
        String startAt,
        String endAt,
        String placeName,
        String address,
        String eventUrl
) {
}
