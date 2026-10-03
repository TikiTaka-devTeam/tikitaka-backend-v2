package com.tikitaka.global.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import com.tikitaka.document.storage.DocumentStorage;
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
    void documentThumbnailCopyPreservesBytesAndSurvivesSourceCleanup() {
        S3Properties properties = new S3Properties();
        properties.setBucket(BUCKET);
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("s3Client", s3Client);
        DocumentStorage storage = new DocumentStorage(beans.getBeanProvider(S3Client.class), properties);
        String source = "documents/source/강의 자료+1.png";
        String target = "documents/completed/강의 썸네일.png";
        byte[] png = {1, 2, 3};

        storage.put(source, png, "image/png");
        storage.copy(source, target);
        storage.delete(source);

        assertThat(storage.get(target)).isEqualTo(png);
        assertThat(s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(target).build())
                .contentType()).isEqualTo("image/png");
        storage.delete(target);
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
