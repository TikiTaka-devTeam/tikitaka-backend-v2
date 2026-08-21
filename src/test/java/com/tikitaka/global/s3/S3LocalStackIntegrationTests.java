package com.tikitaka.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Testcontainers
class S3LocalStackIntegrationTests {
    private static final String BUCKET = "tikitaka-integration-test";

    @Container
    private static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:4.7.0"))
            .withServices(LocalStackContainer.Service.S3);

    private static S3Client s3Client;
    private static S3Service s3Service;

    @BeforeAll
    static void setUp() {
        s3Client = S3Client.builder()
                .endpointOverride(LOCALSTACK.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
                .region(Region.of(LOCALSTACK.getRegion()))
                .forcePathStyle(true)
                .build();
        s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());

        S3Properties properties = new S3Properties();
        properties.setBucket(BUCKET);
        s3Service = new S3Service(s3Client, properties, new S3FileValidator(properties));
    }

    @AfterAll
    static void tearDown() {
        if (s3Client != null) {
            s3Client.close();
        }
    }

    @Test
    void uploadsAndDeletesObjectThroughLocalStack() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.png", "image/png", new byte[] {1, 2, 3});

        S3UploadResult uploaded = s3Service.upload(
                file, "profiles", FileUploadType.PROFILE_IMAGE);

        assertThat(s3Client.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET).key(uploaded.key()).build()).contentLength()).isEqualTo(3);

        s3Service.delete(uploaded.key());

        assertThatThrownBy(() -> s3Client.headObject(HeadObjectRequest.builder()
                .bucket(BUCKET).key(uploaded.key()).build()))
                .isInstanceOf(NoSuchKeyException.class);
    }
}
