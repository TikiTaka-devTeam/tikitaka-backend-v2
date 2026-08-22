package com.tikitaka.global.config;

import java.net.URI;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.tikitaka.global.s3.S3Properties;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(S3Properties.class)
public class S3Config {
    @Bean
    S3Client s3Client(S3Properties properties) {
        S3ClientBuilder builder = S3Client.builder()
                .region(Region.of(properties.getRegion()));

        if (properties.getEndpoint() != null && !properties.getEndpoint().isBlank()) {
            builder.endpointOverride(URI.create(properties.getEndpoint()))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                            properties.getAccessKey(), properties.getSecretKey())));
        }

        return builder.build();
    }
}
