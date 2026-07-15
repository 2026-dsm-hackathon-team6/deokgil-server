package org.example.deokgilserver.common.storage;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * 클라이언트가 S3에 직접 업로드할 수 있도록 presigned PUT URL을 발급한다. 서버는 파일 바이트를
 * 중계하지 않으므로 대용량 업로드로 인한 서버 자원(대역폭/메모리) 소모가 없다 - 대신 서버가 실제
 * 업로드되는 바이트를 보지 못하므로, Content-Type을 서명에 포함시켜(S3가 실제 요청 헤더와
 * 서명값이 다르면 거부) 최소한의 형식 강제만 건다.
 *
 * 잔존 위험: presigned PUT은 Content-Length를 서명에 포함시킬 수 없어(presigned POST의 policy
 * condition과 달리) 파일 크기 자체는 강제하지 못한다 - 크기까지 엄격히 제한하려면 presigned POST나
 * 업로드 후 크기 초과 시 삭제하는 Lambda 트리거 같은 별도 장치가 필요하다.
 */
@Component
public class S3PresignedUploadService {

    private static final Duration UPLOAD_URL_EXPIRATION = Duration.ofMinutes(5);
    private static final Map<String, String> ALLOWED_CONTENT_TYPE_EXTENSIONS = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp"
    );
    // GetUrlRequest는 빈 문자열 key를 거부하므로(IllegalArgumentException), 버킷의 base URL을
    // 얻으려면 임의의 더미 key로 URL을 만든 뒤 그 key 부분을 잘라내는 방식을 쓴다.
    private static final String PREFIX_PROBE_KEY = "___prefix-probe___";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final S3Properties properties;

    public S3PresignedUploadService(S3Client s3Client, S3Presigner s3Presigner, S3Properties properties) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.properties = properties;
    }

    /**
     * keyPrefix(예: "profile-images/{userId}/") 아래에 서버가 직접 생성한 UUID 파일명으로 객체
     * 키를 만든다 - 클라이언트가 보낸 파일명을 그대로 쓰지 않는 이유는, 경로 조작(".."로 다른
     * prefix에 덮어쓰기)이나 다른 사용자 객체와의 이름 충돌을 막기 위함이다.
     */
    public PresignedUpload createPresignedUpload(String keyPrefix, String contentType) {
        String extension = ALLOWED_CONTENT_TYPE_EXTENSIONS.get(contentType);
        if (extension == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
        }

        String key = keyPrefix + UUID.randomUUID() + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(UPLOAD_URL_EXPIRATION)
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);

        String publicUrl = s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(properties.s3().bucket()).key(key).build())
                .toString();

        return new PresignedUpload(presigned.url().toString(), publicUrl, contentType);
    }

    /**
     * imageUrl이 우리 S3 버킷의 객체를 가리킬 때만 삭제한다 - 프로필 이미지가 우리 버킷이 아닌
     * 외부 URL(예: Google 계정 프로필 사진)일 수도 있어서, 소유 여부를 확인하지 않고 무조건
     * 삭제 시도하면 엉뚱한 외부 URL을 잘못 해석해 예외를 던지거나(최악의 경우 다른 시스템에
     * 영향을 주는 요청을 보낼 위험은 없지만) 불필요한 API 호출을 하게 된다.
     */
    public void deleteIfOwnedByBucket(String imageUrl) {
        String key = extractKeyIfOwnedByBucket(imageUrl);
        if (key == null) {
            return;
        }
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.s3().bucket())
                .key(key)
                .build());
    }

    private String extractKeyIfOwnedByBucket(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return null;
        }
        String probeUrl = s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(properties.s3().bucket()).key(PREFIX_PROBE_KEY).build())
                .toString();
        String bucketPrefix = probeUrl.substring(0, probeUrl.length() - PREFIX_PROBE_KEY.length());
        if (!imageUrl.startsWith(bucketPrefix)) {
            return null;
        }
        return imageUrl.substring(bucketPrefix.length());
    }

    public record PresignedUpload(String uploadUrl, String imageUrl, String contentType) {
    }
}
