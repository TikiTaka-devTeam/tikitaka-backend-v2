package com.tikitaka.document.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import com.tikitaka.global.s3.S3Properties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class DocumentStorage {
    private static final Duration URL_TTL = Duration.ofMinutes(10);

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final S3Properties properties;

    public DocumentStorage(ObjectProvider<S3Client> s3ClientProvider, S3Properties properties) {
        this.s3ClientProvider = s3ClientProvider;
        this.properties = properties;
    }

    public void put(String key, byte[] content, String contentType) {
        try {
            client().putObject(
                    PutObjectRequest.builder()
                            .bucket(properties.getBucket())
                            .key(key)
                            .contentType(contentType)
                            .contentLength((long) content.length)
                            .build(),
                    RequestBody.fromBytes(content));
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(CommonErrorCode.S3_UPLOAD_FAILED, exception);
        }
    }

    public String presignedGetUrl(String key) {
        GetObjectRequest get = GetObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build();
        try (S3Presigner presigner = presigner()) {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(URL_TTL)
                            .getObjectRequest(get)
                            .build())
                    .url()
                    .toString();
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(CommonErrorCode.S3_UPLOAD_FAILED, exception);
        }
    }

    public byte[] get(String key) {
        try {
            ResponseBytes<GetObjectResponse> object = client().getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(properties.getBucket()).key(key).build());
            return object.asByteArray();
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(CommonErrorCode.S3_UPLOAD_FAILED, exception);
        }
    }

    public void delete(String key) {
        try {
            client().deleteObject(DeleteObjectRequest.builder()
                    .bucket(properties.getBucket())
                    .key(key)
                    .build());
        } catch (S3Exception | SdkClientException exception) {
            throw new BusinessException(CommonErrorCode.S3_DELETE_FAILED, exception);
        }
    }

    private S3Client client() {
        S3Client client = s3ClientProvider.getIfAvailable();
        if (client == null) {
            throw new BusinessException(CommonErrorCode.S3_UPLOAD_FAILED);
        }
        return client;
    }

    private S3Presigner presigner() {
        S3Presigner.Builder builder = S3Presigner.builder()
                .region(Region.of(properties.getRegion()));
        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()));
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                    properties.getAccessKey(), properties.getSecretKey())));
            builder.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        return builder.build();
    }
}
