package com.tikitaka.assignment.storage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.tikitaka.assignment.entity.SubmissionFile;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import com.tikitaka.global.s3.S3Properties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Component
public class AssignmentSubmissionArchiveStorage {

    private static final Duration DOWNLOAD_URL_TTL =
            Duration.ofMinutes(10);

    private final ObjectProvider<S3Client> s3ClientProvider;
    private final S3Properties properties;

    public AssignmentSubmissionArchiveStorage(
            ObjectProvider<S3Client> s3ClientProvider,
            S3Properties properties
    ) {
        this.s3ClientProvider =
                s3ClientProvider;

        this.properties =
                properties;
    }

    public String createArchive(
            UUID assignmentId,
            List<SubmissionFile> submissionFiles
    ) {
        byte[] archive =
                createZip(
                        submissionFiles
                );

        String key =
                "assignments/"
                        + assignmentId
                        + "/downloads/submissions-"
                        + UUID.randomUUID()
                        + ".zip";

        uploadZip(
                key,
                archive
        );

        return createPresignedUrl(
                key
        );
    }

    private byte[] createZip(
            List<SubmissionFile> submissionFiles
    ) {
        try (
                ByteArrayOutputStream byteStream =
                        new ByteArrayOutputStream();

                ZipOutputStream zipStream =
                        new ZipOutputStream(
                                byteStream
                        )
        ) {

            Set<String> usedEntryNames =
                    new HashSet<>();

            for (SubmissionFile file :
                    submissionFiles) {

                String key =
                        extractManagedKey(
                                file.getFileUrl()
                        );

                byte[] content =
                        downloadObject(
                                key
                        );

                String studentNumber =
                        sanitize(
                                file.getSubmission()
                                        .getStudent()
                                        .getMemberIdNumber()
                        );

                String studentName =
                        sanitize(
                                file.getSubmission()
                                        .getStudent()
                                        .getName()
                        );

                String fileName =
                        sanitize(
                                file.getFileName()
                        );

                String baseEntryName =
                        studentNumber
                                + "_"
                                + studentName
                                + "/"
                                + fileName;

                String entryName =
                        uniqueEntryName(
                                baseEntryName,
                                usedEntryNames
                        );

                ZipEntry entry =
                        new ZipEntry(
                                entryName
                        );

                zipStream.putNextEntry(
                        entry
                );

                zipStream.write(
                        content
                );

                zipStream.closeEntry();
            }

            zipStream.finish();

            return byteStream.toByteArray();

        } catch (IOException exception) {

            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    exception
            );
        }
    }

    private byte[] downloadObject(
            String key
    ) {
        try {

            ResponseBytes<GetObjectResponse> object =
                    client().getObjectAsBytes(
                            GetObjectRequest.builder()
                                    .bucket(
                                            properties.getBucket()
                                    )
                                    .key(
                                            key
                                    )
                                    .build()
                    );

            return object.asByteArray();

        } catch (
                S3Exception
                | SdkClientException exception
        ) {

            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    exception
            );
        }
    }

    private void uploadZip(
            String key,
            byte[] archive
    ) {
        try {

            client().putObject(
                    PutObjectRequest.builder()
                            .bucket(
                                    properties.getBucket()
                            )
                            .key(
                                    key
                            )
                            .contentType(
                                    "application/zip"
                            )
                            .contentLength(
                                    (long) archive.length
                            )
                            .build(),

                    RequestBody.fromBytes(
                            archive
                    )
            );

        } catch (
                S3Exception
                | SdkClientException exception
        ) {

            throw new BusinessException(
                    CommonErrorCode.S3_UPLOAD_FAILED,
                    exception
            );
        }
    }

    private String createPresignedUrl(
            String key
    ) {
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder()
                        .bucket(
                                properties.getBucket()
                        )
                        .key(
                                key
                        )
                        .build();

        try (
                S3Presigner presigner =
                        presigner()
        ) {

            return presigner
                    .presignGetObject(
                            GetObjectPresignRequest.builder()
                                    .signatureDuration(
                                            DOWNLOAD_URL_TTL
                                    )
                                    .getObjectRequest(
                                            getObjectRequest
                                    )
                                    .build()
                    )
                    .url()
                    .toString();

        } catch (
                S3Exception
                | SdkClientException exception
        ) {

            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    exception
            );
        }
    }

    private String extractManagedKey(
            String url
    ) {
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

            if (host == null
                    || path == null
                    || bucket == null
                    || bucket.isBlank()) {

                throw new BusinessException(
                        CommonErrorCode.INTERNAL_SERVER_ERROR
                );
            }

            String normalizedPath =
                    path.replaceFirst(
                            "^/+",
                            ""
                    );

            if (host.startsWith(
                    bucket + "."
            )) {
                return normalizedPath;
            }

            if (normalizedPath.startsWith(
                    bucket + "/"
            )) {
                return normalizedPath.substring(
                        bucket.length() + 1
                );
            }

            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR
            );

        } catch (IllegalArgumentException exception) {

            throw new BusinessException(
                    CommonErrorCode.INTERNAL_SERVER_ERROR,
                    exception
            );
        }
    }

    private String uniqueEntryName(
            String baseName,
            Set<String> usedEntryNames
    ) {
        if (usedEntryNames.add(
                baseName
        )) {
            return baseName;
        }

        int dotIndex =
                baseName.lastIndexOf(
                        '.'
                );

        String name;
        String extension;

        if (dotIndex > 0) {

            name =
                    baseName.substring(
                            0,
                            dotIndex
                    );

            extension =
                    baseName.substring(
                            dotIndex
                    );

        } else {

            name =
                    baseName;

            extension =
                    "";
        }

        int sequence = 2;

        while (true) {

            String candidate =
                    name
                            + "_"
                            + sequence
                            + extension;

            if (usedEntryNames.add(
                    candidate
            )) {
                return candidate;
            }

            sequence++;
        }
    }

    private String sanitize(
            String value
    ) {
        if (value == null
                || value.isBlank()) {
            return "unknown";
        }

        return value
                .replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                )
                .trim();
    }

    private S3Client client() {

        S3Client client =
                s3ClientProvider
                        .getIfAvailable();

        if (client == null) {

            throw new BusinessException(
                    CommonErrorCode.S3_UPLOAD_FAILED
            );
        }

        return client;
    }

    private S3Presigner presigner() {

        S3Presigner.Builder builder =
                S3Presigner.builder()
                        .region(
                                Region.of(
                                        properties.getRegion()
                                )
                        );

        if (properties.getEndpoint() != null
                && !properties
                        .getEndpoint()
                        .isBlank()) {

            builder.endpointOverride(
                    URI.create(
                            properties.getEndpoint()
                    )
            );

            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(
                                    properties.getAccessKey(),
                                    properties.getSecretKey()
                            )
                    )
            );

            builder.serviceConfiguration(
                    S3Configuration.builder()
                            .pathStyleAccessEnabled(
                                    true
                            )
                            .build()
            );

        } else {

            builder.credentialsProvider(
                    DefaultCredentialsProvider.create()
            );
        }

        return builder.build();
    }
}