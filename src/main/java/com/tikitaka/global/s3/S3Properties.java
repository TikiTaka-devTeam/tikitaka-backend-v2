package com.tikitaka.global.s3;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;

@ConfigurationProperties(prefix = "aws.s3")
@Validated
public class S3Properties {
    private static final Set<String> DOCUMENT_CONTENT_TYPES = Set.of(
            "application/pdf", "text/plain", "text/csv",
            "application/x-hwp", "application/haansofthwp", "application/vnd.hancom.hwpx",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "image/jpeg", "image/png", "image/webp", "image/gif",
            "application/zip", "application/x-zip-compressed");
    @NotBlank
    private String bucket;
    @NotBlank
    private String region = "ap-northeast-2";
    private String endpoint;
    private String accessKey;
    private String secretKey;
    @NotEmpty
    private Map<FileUploadType, @Valid UploadPolicy> uploadPolicies = defaultUploadPolicies();

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    @AssertTrue(message = "endpoint를 설정하면 access-key와 secret-key가 필요합니다")
    public boolean isEndpointCredentialsValid() {
        return endpoint == null || endpoint.isBlank()
                || (accessKey != null && !accessKey.isBlank()
                && secretKey != null && !secretKey.isBlank());
    }
    public Map<FileUploadType, UploadPolicy> getUploadPolicies() { return Map.copyOf(uploadPolicies); }
    public void setUploadPolicies(Map<FileUploadType, UploadPolicy> uploadPolicies) {
        this.uploadPolicies = new EnumMap<>(uploadPolicies);
    }

    private static Map<FileUploadType, UploadPolicy> defaultUploadPolicies() {
        Map<FileUploadType, UploadPolicy> policies = new EnumMap<>(FileUploadType.class);
        policies.put(FileUploadType.PROFILE_IMAGE, new UploadPolicy(DataSize.ofMegabytes(10),
                DataSize.ofMegabytes(10), 1, Set.of("image/jpeg", "image/png", "image/webp")));
        policies.put(FileUploadType.LECTURE_DOCUMENT, new UploadPolicy(DataSize.ofMegabytes(10),
                DataSize.ofMegabytes(10), 1, Set.of("application/pdf")));
        policies.put(FileUploadType.NOTICE_ATTACHMENT, new UploadPolicy(DataSize.ofMegabytes(50),
                DataSize.ofMegabytes(100), 10, DOCUMENT_CONTENT_TYPES));
        policies.put(FileUploadType.ASSIGNMENT_ATTACHMENT, new UploadPolicy(DataSize.ofMegabytes(50),
                DataSize.ofMegabytes(100), 10, DOCUMENT_CONTENT_TYPES));
        policies.put(FileUploadType.ASSIGNMENT_SUBMISSION, new UploadPolicy(DataSize.ofMegabytes(50),
                DataSize.ofMegabytes(100), 5, DOCUMENT_CONTENT_TYPES));
        return policies;
    }

    public static class UploadPolicy {
        @NotNull
        private DataSize maxFileSize;
        @NotNull
        private DataSize maxRequestSize;
        @Min(1)
        private int maxFileCount;
        @NotEmpty
        private Set<String> allowedContentTypes;

        public UploadPolicy() {
        }

        public UploadPolicy(DataSize maxFileSize, DataSize maxRequestSize, int maxFileCount,
                            Set<String> allowedContentTypes) {
            this.maxFileSize = maxFileSize;
            this.maxRequestSize = maxRequestSize;
            this.maxFileCount = maxFileCount;
            this.allowedContentTypes = Set.copyOf(allowedContentTypes);
        }

        public DataSize getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(DataSize maxFileSize) { this.maxFileSize = maxFileSize; }
        public DataSize getMaxRequestSize() { return maxRequestSize; }
        public void setMaxRequestSize(DataSize maxRequestSize) { this.maxRequestSize = maxRequestSize; }
        public int getMaxFileCount() { return maxFileCount; }
        public void setMaxFileCount(int maxFileCount) { this.maxFileCount = maxFileCount; }
        public Set<String> getAllowedContentTypes() { return allowedContentTypes; }
        public void setAllowedContentTypes(Set<String> allowedContentTypes) {
            this.allowedContentTypes = Set.copyOf(allowedContentTypes);
        }
    }
}

