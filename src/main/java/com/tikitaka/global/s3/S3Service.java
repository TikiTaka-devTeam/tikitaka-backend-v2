package com.tikitaka.global.s3;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@ConditionalOnBean(S3Client.class)
public class S3Service {

    private final S3Client s3Client;
    private final S3Properties properties;
    private final S3FileValidator validator;

    public S3Service(
            S3Client s3Client,
            S3Properties properties,
            S3FileValidator validator
    ) {
        this.s3Client = s3Client;
        this.properties = properties;
        this.validator = validator;
    }

    public S3UploadResult upload(
            MultipartFile file,
            String directory,
            FileUploadType uploadType
    ) {
        validator.validate(file, uploadType);

        return uploadValidated(
                file,
                directory
        );
    }

    public List<S3UploadResult> uploadAll(
            List<MultipartFile> files,
            String directory,
            FileUploadType uploadType
    ) {

        /*
         * 파일 파트 자체가 전달되지 않은 경우
         */
        if (files == null || files.isEmpty()) {
            return List.of();
        }

        /*
         * multipart/form-data에서 파일을 선택하지 않아도
         * 빈 MultipartFile 객체가 전달될 수 있다.
         *
         * 검증 전에 빈 파일을 제거해야 한다.
         */
        List<MultipartFile> validFiles =
                files.stream()
                        .filter(file ->
                                file != null
                                        && !file.isEmpty()
                                        && file.getSize() > 0
                        )
                        .toList();

        /*
         * 실제 업로드할 파일이 없으면 종료
         */
        if (validFiles.isEmpty()) {
            return List.of();
        }

        /*
         * 실제 파일만 검증
         */
        validator.validateAll(
                validFiles,
                uploadType
        );

        List<S3UploadResult> uploaded =
                new ArrayList<>();

        try {

            for (MultipartFile file : validFiles) {

                uploaded.add(
                        uploadValidated(
                                file,
                                directory
                        )
                );
            }

            return List.copyOf(
                    uploaded
            );

        } catch (BusinessException exception) {

            /*
             * 여러 파일 업로드 중 하나가 실패한 경우
             * 이미 업로드된 파일을 롤백한다.
             */
            for (S3UploadResult result : uploaded) {

                try {

                    delete(
                            result.key()
                    );

                } catch (BusinessException cleanupException) {

                    exception.addSuppressed(
                            cleanupException
                    );
                }
            }

            throw exception;
        }
    }

    private S3UploadResult uploadValidated(
            MultipartFile file,
            String directory
    ) {

        String key =
                createKey(
                        directory,
                        file.getOriginalFilename()
                );

        PutObjectRequest request =
                PutObjectRequest.builder()
                        .bucket(
                                properties.getBucket()
                        )
                        .key(
                                key
                        )
                        .contentType(
                                file.getContentType()
                        )
                        .contentLength(
                                file.getSize()
                        )
                        .build();

        try {

            s3Client.putObject(
                    request,
                    RequestBody.fromInputStream(
                            file.getInputStream(),
                            file.getSize()
                    )
            );

            String url =
                    s3Client.utilities()
                            .getUrl(builder ->
                                    builder
                                            .bucket(
                                                    properties.getBucket()
                                            )
                                            .key(
                                                    key
                                            )
                            )
                            .toString();

            return new S3UploadResult(
                    key,
                    url
            );

        } catch (
                IOException
                | S3Exception
                | SdkClientException exception
        ) {

            throw new BusinessException(
                    CommonErrorCode.S3_UPLOAD_FAILED,
                    exception
            );
        }
    }

    public void delete(
            String key
    ) {

        try {

            s3Client.deleteObject(
                    DeleteObjectRequest.builder()
                            .bucket(
                                    properties.getBucket()
                            )
                            .key(
                                    key
                            )
                            .build()
            );

        } catch (
                S3Exception
                | SdkClientException exception
        ) {

            throw new BusinessException(
                    CommonErrorCode.S3_DELETE_FAILED,
                    exception
            );
        }
    }

    public void deleteByUrlIfManaged(
            String url
    ) {

        managedKey(url)
                .ifPresent(
                        this::delete
                );
    }

    private Optional<String> managedKey(
            String url
    ) {

        if (url == null || url.isBlank()) {
            return Optional.empty();
        }

        try {

            URI uri =
                    URI.create(
                            url
                    );

            String bucket =
                    properties.getBucket();

            String host =
                    uri.getHost();

            String path =
                    uri.getPath();

            if (bucket == null
                    || bucket.isBlank()
                    || host == null
                    || path == null) {

                return Optional.empty();
            }

            String key;

            String normalizedPath =
                    path.replaceFirst(
                            "^/+",
                            ""
                    );

            /*
             * virtual-hosted style
             *
             * bucket.s3.amazonaws.com/key
             */
            if (host.startsWith(
                    bucket + "."
            )) {

                key =
                        normalizedPath;

            /*
             * path style
             *
             * s3.amazonaws.com/bucket/key
             */
            } else if (normalizedPath.startsWith(
                    bucket + "/"
            )) {

                key =
                        normalizedPath.substring(
                                bucket.length() + 1
                        );

            } else {

                return Optional.empty();
            }

            return key.isBlank()
                    ? Optional.empty()
                    : Optional.of(
                            key
                    );

        } catch (IllegalArgumentException exception) {

            return Optional.empty();
        }
    }

    private String createKey(
            String directory,
            String originalFilename
    ) {

        String safeDirectory =
                directory == null
                        ? "files"
                        : directory.replaceAll(
                                "[^a-zA-Z0-9/_-]",
                                ""
                        );

        safeDirectory =
                safeDirectory.replaceAll(
                        "^/+|/+$",
                        ""
                );

        if (safeDirectory.isBlank()) {
            safeDirectory = "files";
        }

        LocalDate today =
                LocalDate.now(
                        ZoneOffset.UTC
                );

        return "%s/%d/%02d/%s%s".formatted(
                safeDirectory,
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                extensionOf(
                        originalFilename
                )
        );
    }

    private String extensionOf(
            String filename
    ) {

        if (filename == null) {
            return "";
        }

        int dotIndex =
                filename.lastIndexOf(
                        '.'
                );

        if (dotIndex < 0
                || dotIndex == filename.length() - 1) {

            return "";
        }

        String extension =
                filename.substring(
                                dotIndex + 1
                        )
                        .replaceAll(
                                "[^a-zA-Z0-9]",
                                ""
                        )
                        .toLowerCase();

        return extension.isBlank()
                ? ""
                : "." + extension;
    }
}