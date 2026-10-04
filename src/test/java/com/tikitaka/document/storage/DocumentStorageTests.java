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
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import java.time.Instant;

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
    void deletesUsingExactObjectKey() {
        storage.delete("documents/file.pdf");

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(request.capture());
        assertThat(request.getValue().key()).isEqualTo("documents/file.pdf");
    }

    @Test
    void cleanupDeletesOnlyExpiredObjectsAndFollowsPagination() {
        Instant cutoff = Instant.parse("2026-10-04T00:00:00Z");
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                ListObjectsV2Response.builder().isTruncated(true).nextContinuationToken("next")
                        .contents(S3Object.builder().key("exports/old.pdf").lastModified(cutoff.minusSeconds(1)).build(),
                                S3Object.builder().key("exports/new.pdf").lastModified(cutoff.plusSeconds(1)).build())
                        .build(),
                ListObjectsV2Response.builder().isTruncated(false)
                        .contents(S3Object.builder().key("exports/older.pdf").lastModified(cutoff.minusSeconds(60)).build())
                        .build());
        when(s3Client.listObjectsV2Paginator(any(ListObjectsV2Request.class))).thenAnswer(invocation ->
                new ListObjectsV2Iterable(s3Client, invocation.getArgument(0)));
        storage.deleteOlderThan("exports/", cutoff);
        ArgumentCaptor<DeleteObjectRequest> deleted = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        org.mockito.Mockito.verify(s3Client, org.mockito.Mockito.times(2)).deleteObject(deleted.capture());
        assertThat(deleted.getAllValues()).extracting(DeleteObjectRequest::key)
                .containsExactly("exports/old.pdf", "exports/older.pdf");
        ArgumentCaptor<ListObjectsV2Request> listed = ArgumentCaptor.forClass(ListObjectsV2Request.class);
        org.mockito.Mockito.verify(s3Client, org.mockito.Mockito.times(2)).listObjectsV2(listed.capture());
        assertThat(listed.getAllValues()).extracting(ListObjectsV2Request::prefix).containsOnly("exports/");
        assertThat(listed.getAllValues().get(1).continuationToken()).isEqualTo("next");
    }

    @Test
    void createsTemporarySignedUrlFromObjectKey() {
        String url = storage.presignedGetUrl("documents/file.pdf");

        assertThat(url).contains("documents/file.pdf");
        assertThat(url).contains("X-Amz-Signature=");
    }
}
