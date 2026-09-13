package com.tikitaka.global.s3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.net.URI;
import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Utilities;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3ServiceTests {
    private S3Client s3Client;
    private S3Service service;

    @BeforeEach
    void setUp() {
        s3Client = mock(S3Client.class);
        S3Properties properties = new S3Properties();
        properties.setBucket("test-bucket");
        service = new S3Service(s3Client, properties, new S3FileValidator(properties));
    }

    @Test
    void namedProfileUploadPreservesKoreanAndCanBeDeletedByReturnedUrl() {
        when(s3Client.utilities()).thenReturn(S3Utilities.builder().region(Region.AP_NORTHEAST_2).build());
        MockMultipartFile file = new MockMultipartFile("file", "avatar.PNG", "image/png", new byte[] {1});

        S3UploadResult first = service.uploadProfileImage(file, " 홍길동/교수 ");
        S3UploadResult second = service.uploadProfileImage(file, " 홍길동/교수 ");

        assertThat(first.key()).contains("/홍길동_교수_프로필이미지_").endsWith(".png");
        assertThat(first.key()).isNotEqualTo(second.key());
        assertThat(URI.create(first.url()).getPath()).isEqualTo("/" + first.key());
        service.deleteByUrlIfManaged(first.url());
        ArgumentCaptor<DeleteObjectRequest> deletion = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(deletion.capture());
        assertThat(deletion.getValue().key()).isEqualTo(first.key());
    }

    @Test
    void signsExistingPrivateProfileUrlWithEncodedKeyAndExpiry() {
        S3Properties properties = new S3Properties();
        properties.setBucket("test-bucket");
        properties.setEndpoint("http://localhost:4566");
        properties.setAccessKey("test");
        properties.setSecretKey("test");
        S3Service localService = new S3Service(s3Client, properties, new S3FileValidator(properties));

        String signed = localService.presignedProfileUrl(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/2026/09/%ED%99%8D.png");

        assertThat(signed).contains("X-Amz-Signature=", "X-Amz-Expires=600");
        assertThat(URI.create(signed).getPath()).isEqualTo("/test-bucket/profiles/2026/09/홍.png");
    }

    @Test
    void doesNotSignOrDeleteExternalUrlsEvenWhenBucketNameAppearsInHostOrPath() {
        for (String url : List.of(
                "https://test-bucket.evil.example/profiles/image.png",
                "https://evil.example/test-bucket/profiles/image.png",
                "https://oauth.example/avatar.png")) {
            assertThat(service.presignedProfileUrl(url)).isEqualTo(url);
            service.deleteByUrlIfManaged(url);
        }
        assertThat(service.presignedProfileUrl(null)).isNull();
        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void convertsClientUploadFailureToCommonException() {
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.builder().message("sensitive endpoint detail").build());
        MockMultipartFile file = new MockMultipartFile(
                "file", "image.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> service.upload(file, "profiles", FileUploadType.PROFILE_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.S3_UPLOAD_FAILED);
    }

    @Test
    void deletesManagedObjectResolvedFromVirtualHostedUrl() {
        service.deleteByUrlIfManaged(
                "https://test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/key.png");

        verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void ignoresExternalProfileUrl() {
        service.deleteByUrlIfManaged("https://lh3.googleusercontent.com/profile.png");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void convertsClientDeleteFailureToCommonException() {
        when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.builder().message("sensitive endpoint detail").build());

        assertThatThrownBy(() -> service.delete("profiles/key.png"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.S3_DELETE_FAILED);
    }

    @Test
    void deletesPreviouslyUploadedObjectsWhenBatchUploadFails() {
        when(s3Client.utilities()).thenReturn(
                S3Utilities.builder().region(Region.AP_NORTHEAST_2).build());
        when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build())
                .thenThrow(SdkClientException.builder().message("second upload failed").build());
        MockMultipartFile first = new MockMultipartFile(
                "files", "first.png", "image/png", new byte[] {1});
        MockMultipartFile second = new MockMultipartFile(
                "files", "second.png", "image/png", new byte[] {1});

        assertThatThrownBy(() -> service.uploadAll(
                List.of(first, second), "notices", FileUploadType.NOTICE_ATTACHMENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.S3_UPLOAD_FAILED);
        verify(s3Client, times(1)).deleteObject(any(DeleteObjectRequest.class));
    }
}
