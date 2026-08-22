package com.tikitaka.global.s3;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;

class S3FileValidatorTests {
    private final S3Properties properties = new S3Properties();
    private final S3FileValidator validator = new S3FileValidator(properties);

    @Test
    void rejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("file", "test.exe",
                "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> validator.validate(file, FileUploadType.PROFILE_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    void rejectsOversizedFile() {
        Map<FileUploadType, S3Properties.UploadPolicy> policies =
                new EnumMap<>(properties.getUploadPolicies());
        policies.put(FileUploadType.PROFILE_IMAGE,
                new S3Properties.UploadPolicy(
                        DataSize.ofBytes(1), DataSize.ofBytes(1), 1, Set.of("image/png")));
        properties.setUploadPolicies(policies);
        MockMultipartFile file = new MockMultipartFile("file", "test.png", "image/png", new byte[] {1, 2});

        assertThatThrownBy(() -> validator.validate(file, FileUploadType.PROFILE_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.FILE_SIZE_EXCEEDED);
    }

    @Test
    void acceptsPdfForLectureDocument() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "lecture.pdf", "application/pdf", new byte[] {1});

        validator.validate(file, FileUploadType.LECTURE_DOCUMENT);
    }

    @Test
    void rejectsPdfForProfileImage() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "profile.pdf", "application/pdf", new byte[] {1});

        assertThatThrownBy(() -> validator.validate(file, FileUploadType.PROFILE_IMAGE))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_FILE_TYPE);
    }

    @Test
    void acceptsMixedNoticeAttachments() {
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "notice.pdf", "application/pdf", new byte[] {1}),
                new MockMultipartFile("files", "schedule.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] {1}),
                new MockMultipartFile("files", "map.png", "image/png", new byte[] {1}),
                new MockMultipartFile("files", "resources.zip", "application/zip", new byte[] {1}));

        validator.validateAll(files, FileUploadType.NOTICE_ATTACHMENT);
    }

    @Test
    void rejectsTooManyNoticeAttachments() {
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 11)
                .mapToObj(index -> (MultipartFile) new MockMultipartFile(
                        "files", "image-" + index + ".png", "image/png", new byte[] {1}))
                .toList();

        assertThatThrownBy(() -> validator.validateAll(files, FileUploadType.NOTICE_ATTACHMENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.FILE_COUNT_EXCEEDED);
    }

    @Test
    void rejectsNoticeAttachmentsOverTotalSize() {
        Map<FileUploadType, S3Properties.UploadPolicy> policies =
                new EnumMap<>(properties.getUploadPolicies());
        policies.put(FileUploadType.NOTICE_ATTACHMENT,
                new S3Properties.UploadPolicy(
                        DataSize.ofBytes(2), DataSize.ofBytes(3), 10,
                        Set.of("application/pdf", "image/png")));
        properties.setUploadPolicies(policies);
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "notice.pdf", "application/pdf", new byte[] {1, 2}),
                new MockMultipartFile("files", "map.png", "image/png", new byte[] {1, 2}));

        assertThatThrownBy(() -> validator.validateAll(files, FileUploadType.NOTICE_ATTACHMENT))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.REQUEST_SIZE_EXCEEDED);
    }
    @Test
    void acceptsAssignmentAttachmentDocumentTypes() {
        List<MultipartFile> files = List.of(
                new MockMultipartFile("files", "guide.hwp", "application/x-hwp", new byte[] {1}),
                new MockMultipartFile("files", "starter.zip", "application/zip", new byte[] {1}));

        validator.validateAll(files, FileUploadType.ASSIGNMENT_ATTACHMENT);
    }

    @Test
    void acceptsUpToFiveAssignmentSubmissionFiles() {
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 5)
                .mapToObj(index -> (MultipartFile) new MockMultipartFile(
                        "files", "submission-" + index + ".pdf", "application/pdf", new byte[] {1}))
                .toList();

        validator.validateAll(files, FileUploadType.ASSIGNMENT_SUBMISSION);
    }

    @Test
    void rejectsMoreThanFiveAssignmentSubmissionFiles() {
        List<MultipartFile> files = java.util.stream.IntStream.range(0, 6)
                .mapToObj(index -> (MultipartFile) new MockMultipartFile(
                        "files", "submission-" + index + ".pdf", "application/pdf", new byte[] {1}))
                .toList();

        assertThatThrownBy(() -> validator.validateAll(files, FileUploadType.ASSIGNMENT_SUBMISSION))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.FILE_COUNT_EXCEEDED);
    }

    @Test
    void rejectsExecutableAssignmentSubmission() {
        MockMultipartFile file = new MockMultipartFile(
                "files", "malware.exe", "application/octet-stream", new byte[] {1});

        assertThatThrownBy(() -> validator.validate(file, FileUploadType.ASSIGNMENT_SUBMISSION))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_FILE_TYPE);
    }
}
