package com.tikitaka.document.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.tikitaka.global.s3.S3Properties;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class DocumentStorageTests {
    private final S3Client s3Client = mock(S3Client.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<S3Client> clientProvider = mock(ObjectProvider.class);
    private DocumentStorage storage;

    @BeforeEach
    void setUp() {
        S3Properties properties = new S3Properties();
        properties.setBucket("test-bucket");
        properties.setRegion("ap-northeast-2");
        properties.setEndpoint("http://localhost:4566");
        properties.setAccessKey("test");
        properties.setSecretKey("test");
        when(clientProvider.getIfAvailable()).thenReturn(s3Client);
        storage = new DocumentStorage(clientProvider, properties);
    }

    @Test
    void uploadsBytesUsingExactObjectKey() {
        storage.put("documents/file.pdf", new byte[] {1, 2, 3}, "application/pdf");

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(request.capture(), any(RequestBody.class));
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("documents/file.pdf");
        assertThat(request.getValue().contentType()).isEqualTo("application/pdf");
    }

    @Test
    void copiesExistingThumbnailWithEncodedKoreanAndSpecialCharacters() {
        storage.copy("documents/강의 자료+1.png", "documents/completed/copy.png");
        ArgumentCaptor<CopyObjectRequest> request = ArgumentCaptor.forClass(CopyObjectRequest.class);
        verify(s3Client).copyObject(request.capture());
        assertThat(request.getValue().copySource())
                .isEqualTo("test-bucket/documents/%EA%B0%95%EC%9D%98%20%EC%9E%90%EB%A3%8C%2B1.png");
        assertThat(request.getValue().bucket()).isEqualTo("test-bucket");
        assertThat(request.getValue().key()).isEqualTo("documents/completed/copy.png");
    }

    @Test
    void deletesUsingExactObjectKey() {
        storage.delete("documents/file.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("documents/file.pdf");
    }

    @Test
    void createsTemporarySignedUrlFromObjectKey() {
        String url = storage.presignedGetUrl("documents/file.pdf");

        assertThat(url).contains("documents/file.pdf");
        assertThat(url).contains("X-Amz-Signature=");
    }
}
