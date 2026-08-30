package com.tikitaka.notice.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.FileUploadType;
import com.tikitaka.global.s3.S3Service;
import com.tikitaka.global.s3.S3UploadResult;
import com.tikitaka.notice.dto.request.NoticeCreateRequest;
import com.tikitaka.notice.dto.request.NoticeUpdateRequest;
import com.tikitaka.notice.dto.response.NoticeCreateResponse;
import com.tikitaka.notice.dto.response.NoticeDetailResponse;
import com.tikitaka.notice.dto.response.NoticeFileResponse;
import com.tikitaka.notice.dto.response.NoticeListItemResponse;
import com.tikitaka.notice.dto.response.NoticeListResponse;
import com.tikitaka.notice.dto.response.NoticeUpdateResponse;
import com.tikitaka.notice.entity.NoticeFile;
import com.tikitaka.notice.entity.NoticeRead;
import com.tikitaka.notice.entity.SpaceNotice;
import com.tikitaka.notice.exception.NoticeErrorCode;
import com.tikitaka.notice.repository.NoticeFileRepository;
import com.tikitaka.notice.repository.NoticeReadRepository;
import com.tikitaka.notice.repository.SpaceNoticeRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.exception.SpaceMemberErrorCode;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int PREVIEW_LENGTH = 50;

    private final SpaceNoticeRepository noticeRepository;
    private final NoticeFileRepository noticeFileRepository;
    private final NoticeReadRepository noticeReadRepository;
    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;
    private final S3Service s3Service;
    private final CursorCodec cursorCodec;

    // NOT-001
    public NoticeListResponse getNotices(
            UUID spaceId,
            String cursor,
            int size,
            User currentUser
    ) {
        requireApprovedMember(
                spaceId,
                currentUser
        );

        int pageSize =
                normalizeSize(size);

        NoticeCursor decoded =
                cursorCodec.decodeOrNull(
                        cursor,
                        NoticeCursor.class
                );

        List<SpaceNotice> fetched;

        if (decoded == null) {

            fetched =
                    noticeRepository.findFirstPage(
                            spaceId,
                            PageRequest.of(
                                    0,
                                    pageSize + 1
                            )
                    );

        } else {

            fetched =
                    noticeRepository.findNextPage(
                            spaceId,
                            decoded.createdAt(),
                            decoded.id(),
                            PageRequest.of(
                                    0,
                                    pageSize + 1
                            )
                    );
        }

        boolean hasNext =
                fetched.size() > pageSize;

        List<SpaceNotice> page =
                hasNext
                        ? fetched.subList(
                                0,
                                pageSize
                        )
                        : fetched;

        List<NoticeListItemResponse> notices =
                page.stream()
                        .map(notice ->
                                new NoticeListItemResponse(
                                        notice.getId(),
                                        notice.getTitle(),
                                        preview(
                                                notice.getContent()
                                        ),
                                        notice.getCreatedAt(),
                                        noticeReadRepository
                                                .existsByNoticeIdAndUserId(
                                                        notice.getId(),
                                                        currentUser.getId()
                                                )
                                )
                        )
                        .toList();

        String nextCursor = null;

        if (hasNext
                && !page.isEmpty()) {

            SpaceNotice last =
                    page.get(
                            page.size() - 1
                    );

            nextCursor =
                    cursorCodec.encode(
                            new NoticeCursor(
                                    last.getCreatedAt(),
                                    last.getId()
                            )
                    );
        }

        long totalCount =
                noticeRepository
                        .countBySpaceId(
                                spaceId
                        );

        long unreadCount =
                noticeReadRepository
                        .countUnread(
                                spaceId,
                                currentUser.getId()
                        );

        return new NoticeListResponse(
                totalCount,
                unreadCount,
                notices,
                nextCursor,
                hasNext
        );
    }

    // NOT-002
    @Transactional
    public NoticeDetailResponse getNotice(
            UUID noticeId,
            User currentUser
    ) {
        SpaceNotice notice =
                getNoticeEntity(
                        noticeId
                );

        requireApprovedMember(
                notice.getSpace()
                        .getId(),
                currentUser
        );

        NoticeRead read =
                noticeReadRepository
                        .findByNoticeIdAndUserId(
                                noticeId,
                                currentUser.getId()
                        )
                        .orElseGet(() ->
                                noticeReadRepository.save(
                                        NoticeRead.create(
                                                notice,
                                                currentUser
                                        )
                                )
                        );

        notice.increaseViewCount();

        List<NoticeFileResponse> files =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        )
                        .stream()
                        .map(file ->
                                new NoticeFileResponse(
                                        file.getId(),
                                        file.getFileName(),
                                        file.getFileUrl()
                                )
                        )
                        .toList();

        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getCreatedAt(),
                notice.getAuthor()
                        .getName(),
                notice.getViewCount(),
                true,
                read.getReadAt(),
                notice.getContent(),
                files
        );
    }

    // NOT-003
    @Transactional
    public NoticeCreateResponse createNotice(
            UUID spaceId,
            NoticeCreateRequest request,
            List<MultipartFile> files,
            User currentUser
    ) {
        SpaceMember member =
                requireNoticeManager(
                        spaceId,
                        currentUser
                );

        SpaceNotice notice =
                SpaceNotice.create(
                        member.getSpace(),
                        currentUser,
                        request.title()
                                .trim(),
                        request.content()
                                .trim()
                );

        noticeRepository.save(
                notice
        );

        /*
         * 신규 파일 업로드.
         *
         * DB 트랜잭션이 rollback되면
         * 신규 S3 파일도 삭제한다.
         */
        uploadFiles(
                notice,
                files
        );

        return new NoticeCreateResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getCreatedAt()
        );
    }

    // NOT-004
    @Transactional
    public NoticeUpdateResponse updateNotice(
            UUID noticeId,
            NoticeUpdateRequest request,
            List<MultipartFile> newFiles,
            User currentUser
    ) {
        SpaceNotice notice =
                getNoticeEntity(
                        noticeId
                );

        requireNoticeManager(
                notice.getSpace()
                        .getId(),
                currentUser
        );

        notice.update(
                request.title()
                        .trim(),
                request.content()
                        .trim()
        );

        List<NoticeFile> existingFiles =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        );

        List<UUID> retainedFileIds =
                request.retainedFileIds();

        /*
         * null이면 기존 첨부파일 전체 유지.
         *
         * 빈 배열이면 전체 삭제.
         *
         * 일부 ID면 전달받은 파일만 유지.
         */
        if (retainedFileIds != null) {

            Set<UUID> retainedSet =
                    new HashSet<>(
                            retainedFileIds
                    );

            Set<UUID> existingFileIds =
                    existingFiles.stream()
                            .map(
                                    NoticeFile::getId
                            )
                            .collect(
                                    java.util.stream.Collectors.toSet()
                            );

            /*
             * 다른 공지사항의 파일 ID가 들어오면 차단.
             */
            if (!existingFileIds
                    .containsAll(
                            retainedSet
                    )) {

                throw new BusinessException(
                        NoticeErrorCode
                                .INVALID_RETAINED_FILE
                );
            }

            List<NoticeFile> filesToDelete =
                    existingFiles.stream()
                            .filter(file ->
                                    !retainedSet.contains(
                                            file.getId()
                                    )
                            )
                            .toList();

            /*
             * DB commit 이후 S3에서 삭제하기 위해
             * URL만 미리 보관한다.
             */
            List<String> urlsToDelete =
                    filesToDelete.stream()
                            .map(
                                    NoticeFile::getFileUrl
                            )
                            .toList();

            if (!filesToDelete.isEmpty()) {

                /*
                 * DB 데이터는 현재 트랜잭션 안에서 먼저 삭제.
                 */
                noticeFileRepository.deleteAll(
                        filesToDelete
                );

                /*
                 * 실제 S3 파일은 DB commit 성공 이후 삭제.
                 */
                deleteS3FilesAfterCommit(
                        urlsToDelete
                );
            }
        }

        /*
         * 신규 파일 업로드.
         *
         * 이후 DB rollback 시 신규 S3 파일은 정리된다.
         */
        uploadFiles(
                notice,
                newFiles
        );

        List<NoticeFileResponse> resultFiles =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        )
                        .stream()
                        .map(file ->
                                new NoticeFileResponse(
                                        file.getId(),
                                        file.getFileName(),
                                        file.getFileUrl()
                                )
                        )
                        .toList();

        return new NoticeUpdateResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                resultFiles,
                Instant.now()
        );
    }

    // NOT-005
    @Transactional
    public void deleteNotice(
            UUID noticeId,
            User currentUser
    ) {
        SpaceNotice notice =
                getNoticeEntity(
                        noticeId
                );

        requireNoticeManager(
                notice.getSpace()
                        .getId(),
                currentUser
        );

        List<NoticeFile> files =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        );

        /*
         * commit 이후 삭제할 S3 URL 확보.
         */
        List<String> fileUrls =
                files.stream()
                        .map(
                                NoticeFile::getFileUrl
                        )
                        .toList();

        /*
         * S3를 먼저 삭제하지 않는다.
         *
         * DB 데이터부터 삭제한다.
         */
        noticeReadRepository
                .deleteAllByNoticeId(
                        noticeId
                );

        noticeFileRepository
                .deleteAllByNoticeId(
                        noticeId
                );

        noticeRepository.delete(
                notice
        );

        /*
         * DB commit 성공 후에만
         * 실제 S3 객체 삭제.
         */
        deleteS3FilesAfterCommit(
                fileUrls
        );
    }

    /**
     * 신규 첨부파일 업로드
     */
    private void uploadFiles(
            SpaceNotice notice,
            List<MultipartFile> files
    ) {
        if (files == null
                || files.isEmpty()) {

            return;
        }

        List<MultipartFile> validFiles =
                files.stream()
                        .filter(file ->
                                file != null
                                        && !file.isEmpty()
                                        && file.getSize() > 0
                        )
                        .toList();

        if (validFiles.isEmpty()) {
            return;
        }

        List<S3UploadResult> uploaded =
                s3Service.uploadAll(
                        validFiles,
                        "notices",
                        FileUploadType.NOTICE_ATTACHMENT
                );

        /*
         * S3에는 올라갔는데 이후 DB 작업이 실패하면
         * 신규 업로드 파일을 제거한다.
         */
        deleteUploadedFilesAfterRollback(
                uploaded
        );

        for (int i = 0;
             i < uploaded.size();
             i++) {

            MultipartFile multipartFile =
                    validFiles.get(i);

            S3UploadResult result =
                    uploaded.get(i);

            NoticeFile noticeFile =
                    NoticeFile.create(
                            notice,
                            multipartFile
                                    .getOriginalFilename(),
                            result.url()
                    );

            noticeFileRepository.save(
                    noticeFile
            );
        }
    }

    /**
     * DB COMMIT 성공 후 기존 S3 파일 삭제.
     */
    private void deleteS3FilesAfterCommit(
            List<String> fileUrls
    ) {
        if (fileUrls == null
                || fileUrls.isEmpty()) {

            return;
        }

        List<String> urls =
                List.copyOf(
                        fileUrls
                );

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCommit() {

                                for (String fileUrl : urls) {

                                    try {

                                        s3Service.delete(
                                                fileUrl
                                        );

                                    } catch (Exception ignored) {

                                        /*
                                         * DB commit 완료 후이므로
                                         * S3 삭제 실패로 DB를 rollback할 수 없다.
                                         *
                                         * 추후 로그 / 재시도 처리 가능.
                                         */
                                    }
                                }
                            }
                        }
                );
    }

    /**
     * DB rollback 시 신규 업로드 S3 파일 제거.
     */
    private void deleteUploadedFilesAfterRollback(
            List<S3UploadResult> uploadedFiles
    ) {
        if (uploadedFiles == null
                || uploadedFiles.isEmpty()) {

            return;
        }

        List<S3UploadResult> uploaded =
                List.copyOf(
                        uploadedFiles
                );

        TransactionSynchronizationManager
                .registerSynchronization(
                        new TransactionSynchronization() {

                            @Override
                            public void afterCompletion(
                                    int status
                            ) {
                                if (status
                                        != TransactionSynchronization
                                        .STATUS_ROLLED_BACK) {

                                    return;
                                }

                                for (S3UploadResult result :
                                        uploaded) {

                                    try {

                                        s3Service.delete(
                                                result.url()
                                        );

                                    } catch (Exception ignored) {

                                        /*
                                         * DB rollback은 이미 완료된 상태.
                                         * S3 삭제 실패는 별도 재시도 대상.
                                         */
                                    }
                                }
                            }
                        }
                );
    }

    private SpaceNotice getNoticeEntity(
            UUID noticeId
    ) {
        return noticeRepository
                .findById(
                        noticeId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                NoticeErrorCode
                                        .NOTICE_NOT_FOUND
                        )
                );
    }

    private SpaceMember requireApprovedMember(
            UUID spaceId,
            User currentUser
    ) {
        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        currentUser.getId(),
                        SpaceMemberStatus.APPROVED
                )
                .orElseThrow(() ->
                        new BusinessException(
                                SpaceMemberErrorCode
                                        .SPACE_ACCESS_DENIED
                        )
                );
    }

    private SpaceMember requireNoticeManager(
            UUID spaceId,
            User currentUser
    ) {
        SpaceMember member =
                requireApprovedMember(
                        spaceId,
                        currentUser
                );

        /*
         * 교수는 공지 관리 가능.
         */
        if (member.getRole()
                == SpaceMemberRole.PROFESSOR) {

            return member;
        }

        /*
         * 조교는 NOTICE_MANAGE 권한이 있을 때만 가능.
         *
         * 실제 SpaceMemberPermissionRepository에서
         * 사용하는 메서드명:
         *
         * existsBySpaceMemberIdAndPermission
         */
        if (member.getRole()
                == SpaceMemberRole.ASSISTANT
                && permissionRepository
                .existsBySpaceMemberIdAndPermission(
                        member.getId(),
                        PermissionType.NOTICE_MANAGE
                )) {

            return member;
        }

        throw new BusinessException(
                SpaceMemberErrorCode
                        .SPACE_ACCESS_DENIED
        );
    }

    private int normalizeSize(
            int size
    ) {
        if (size <= 0) {
            return DEFAULT_SIZE;
        }

        return Math.min(
                size,
                MAX_SIZE
        );
    }

    private String preview(
            String content
    ) {
        String normalized =
                content
                        .replaceAll(
                                "\\s+",
                                " "
                        )
                        .trim();

        if (normalized.length()
                <= PREVIEW_LENGTH) {

            return normalized;
        }

        return normalized.substring(
                0,
                PREVIEW_LENGTH
        ) + "...";
    }

    private record NoticeCursor(
            Instant createdAt,
            UUID id
    ) {
    }
}