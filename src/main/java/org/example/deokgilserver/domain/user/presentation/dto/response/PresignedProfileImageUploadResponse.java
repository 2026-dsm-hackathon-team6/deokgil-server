package org.example.deokgilserver.domain.user.presentation.dto.response;

/**
 * uploadUrl: 클라이언트가 이 URL로 이미지 바이트를 직접 PUT한다(Content-Type 헤더를 요청 시
 * 보낸 contentType과 정확히 같게 설정해야 S3 서명 검증을 통과한다).
 * imageUrl: 업로드 완료 후 접근 가능한 최종 공개 URL - PATCH /api/v1/users/me 호출 시
 * profileImage 필드에 이 값을 그대로 넣어 저장하면 된다.
 */
public record PresignedProfileImageUploadResponse(String uploadUrl, String imageUrl) {
}
