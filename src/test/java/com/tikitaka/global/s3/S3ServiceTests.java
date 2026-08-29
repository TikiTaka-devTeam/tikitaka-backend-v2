package com.tikitaka.global.s3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
