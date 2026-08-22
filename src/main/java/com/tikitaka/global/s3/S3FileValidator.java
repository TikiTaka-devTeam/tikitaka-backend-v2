package com.tikitaka.global.s3;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;

@Component
@ConditionalOnBean(S3Properties.class)
public class S3FileValidator {
    private final S3Properties properties;

    public S3FileValidator(S3Properties properties) { this.properties = properties; }

    public void validate(MultipartFile file, FileUploadType uploadType) {
        if (file == null || file.isEmpty()) throw new BusinessException(CommonErrorCode.EMPTY_FILE);
        S3Properties.UploadPolicy policy = properties.getUploadPolicies().get(uploadType);
        if (policy == null) throw new IllegalStateException("S3 upload policy is missing: " + uploadType);
        if (file.getSize() > policy.getMaxFileSize().toBytes()) {
            throw new BusinessException(CommonErrorCode.FILE_SIZE_EXCEEDED);
        }
        if (!policy.getAllowedContentTypes().contains(file.getContentType())) {
            throw new BusinessException(CommonErrorCode.INVALID_FILE_TYPE);
        }
    }

    public void validateAll(List<MultipartFile> files, FileUploadType uploadType) {
        if (files == null || files.isEmpty()) return;
        S3Properties.UploadPolicy policy = properties.getUploadPolicies().get(uploadType);
        if (policy == null) throw new IllegalStateException("S3 upload policy is missing: " + uploadType);
        if (files.size() > policy.getMaxFileCount()) {
            throw new BusinessException(CommonErrorCode.FILE_COUNT_EXCEEDED);
        }

        long totalSize = 0;
        for (MultipartFile file : files) {
            validate(file, uploadType);
            totalSize += file.getSize();
            if (totalSize > policy.getMaxRequestSize().toBytes()) {
                throw new BusinessException(CommonErrorCode.REQUEST_SIZE_EXCEEDED);
            }
        }
    }
}
