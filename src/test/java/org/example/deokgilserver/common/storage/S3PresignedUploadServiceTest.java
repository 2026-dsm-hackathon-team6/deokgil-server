package org.example.deokgilserver.common.storage;

import org.example.deokgilserver.common.exception.BusinessException;
import org.example.deokgilserver.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * S3Client/S3Presigner의 URL 계산(utilities().getUrl())과 presigned URL 서명은 실제 네트워크
 * 호출 없이 로컬에서만 이뤄지는 순수 연산이라, 가짜 자격증명으로 만든 실제 인스턴스를 그대로
 * 써도 안전하게 테스트할 수 있다 - 유일하게 실제 네트워크를 타는 deleteObject만 별도로 spy로
 * 감싸 실제 호출은 막고 호출 여부만 검증한다.
 */
class S3PresignedUploadServiceTest {

    private S3Properties properties;
    private S3PresignedUploadService service;
    private S3Client s3ClientSpy;

    @BeforeEach
    void setUp() {
        properties = new S3Properties(
                "ap-northeast-2", "test-access-key", "test-secret-key", new S3Properties.S3("test-bucket"));

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));

        S3Client realS3Client = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .build();
        S3Presigner presigner = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials)
                .build();

        s3ClientSpy = spy(realS3Client);
        // deleteObject는 실제 네트워크 호출이라 테스트에서 실행하면 안 되므로 아무 동작도
        //하지 않도록 막는다 - 호출 여부/인자만 verify로 확인한다.
        doNothingOnDelete(s3ClientSpy);

        service = new S3PresignedUploadService(s3ClientSpy, presigner, properties);
    }

    private static void doNothingOnDelete(S3Client spyClient) {
        doReturn(null).when(spyClient).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 허용된_content_type이면_presigned_URL과_공개_URL을_발급한다() {
        S3PresignedUploadService.PresignedUpload upload =
                service.createPresignedUpload("profile-images/user-1/", "image/png");

        assertThat(upload.uploadUrl()).contains("test-bucket");
        assertThat(upload.uploadUrl()).contains("X-Amz-Signature");
        assertThat(upload.imageUrl()).startsWith("https://");
        assertThat(upload.imageUrl()).contains("profile-images/user-1/");
        assertThat(upload.imageUrl()).endsWith(".png");
        assertThat(upload.contentType()).isEqualTo("image/png");
    }

    @Test
    void 허용되지_않은_content_type이면_예외가_발생한다() {
        assertThatThrownBy(() -> service.createPresignedUpload("profile-images/user-1/", "application/pdf"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNSUPPORTED_IMAGE_TYPE);
    }

    @Test
    void 같은_prefix로_두번_발급하면_서로_다른_키가_생성된다() {
        S3PresignedUploadService.PresignedUpload first =
                service.createPresignedUpload("profile-images/user-1/", "image/jpeg");
        S3PresignedUploadService.PresignedUpload second =
                service.createPresignedUpload("profile-images/user-1/", "image/jpeg");

        assertThat(first.imageUrl()).isNotEqualTo(second.imageUrl());
    }

    @Test
    void 우리_버킷_소속_URL이면_삭제를_시도한다() {
        S3PresignedUploadService.PresignedUpload upload =
                service.createPresignedUpload("profile-images/user-1/", "image/jpeg");

        service.deleteIfOwnedByBucket(upload.imageUrl());

        verify(s3ClientSpy, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void 우리_버킷_소속이_아닌_URL이면_삭제를_시도하지_않는다() {
        service.deleteIfOwnedByBucket("https://lh3.googleusercontent.com/a/some-google-profile-photo");

        verify(s3ClientSpy, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void null_URL이면_삭제를_시도하지_않는다() {
        service.deleteIfOwnedByBucket(null);

        verify(s3ClientSpy, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
