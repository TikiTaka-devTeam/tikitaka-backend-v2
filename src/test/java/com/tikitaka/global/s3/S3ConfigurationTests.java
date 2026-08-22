package com.tikitaka.global.s3;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.tikitaka.global.config.S3Config;

import software.amazon.awssdk.services.s3.S3Client;

class S3ConfigurationTests {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(S3Config.class);

    @Test
    void missingBucketFailsContextStartup() {
        contextRunner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("bucket");
        });
    }

    @Test
    void testProfileDoesNotRequireS3Configuration() {
        contextRunner.withPropertyValues("spring.profiles.active=test")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void localEndpointRequiresCredentials() {
        contextRunner.withPropertyValues(
                        "spring.profiles.active=local",
                        "aws.s3.bucket=test-bucket",
                        "aws.s3.endpoint=http://localhost:4566")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).rootCause()
                            .hasMessageContaining("access-key");
                });
    }

    @Test
    void localEndpointCreatesS3ClientWithDummyCredentials() {
        contextRunner.withPropertyValues(
                        "spring.profiles.active=local",
                        "aws.s3.bucket=test-bucket",
                        "aws.s3.endpoint=http://localhost:4566",
                        "aws.s3.access-key=test",
                        "aws.s3.secret-key=test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }
}
