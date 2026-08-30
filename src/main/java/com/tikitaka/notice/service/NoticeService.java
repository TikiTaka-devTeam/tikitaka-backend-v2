package com.tikitaka.notice.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    private final SpaceNoticeRepository spaceNoticeRepository;
    private final NoticeFileRepository noticeFileRepository;
    private final NoticeReadRepository noticeReadRepository;

    private final SpaceMemberRepository spaceMemberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;

    private final S3Service s3Service;
    private final CursorCodec cursorCodec;

    /**
     * NOT-001
     * 공지사항 목록 조회
     */
    public NoticeListResponse getNotices(
            UUID spaceId,
            String cursor,
            int size,
            User currentUser
    ) {
        requireApprovedMember(
                spaceId,
                currentUser.getId()
        );

        int pageSize = normalizeSize(size);

        NoticeCursor decoded =
                cursorCodec.decodeOrNull(
                        cursor,
                        NoticeCursor.class
                );

        List<SpaceNotice> fetched;

        /*
         * 첫 조회와 다음 페이지 조회를 분리한다.
         *
         * 첫 조회에서 createdAt = null, id = null 값을
         * JPQL 조건에 전달하면 DB/Hibernate 환경에 따라
         * 타입 추론 문제가 발생할 수 있으므로 별도 쿼리를 사용한다.
         */
        if (decoded == null) {

            fetched =
                    spaceNoticeRepository.findFirstPage(
                            spaceId,
                            PageRequest.of(
                                    0,
                                    pageSize + 1
                            )
                    );

        } else {

            fetched =
                    spaceNoticeRepository.findNextPage(
                            spaceId,
                            decoded.createdAt(),
                            decoded.id(),
                            PageRequest.of(
                                    0,
                                    pageSize + 1
                            )
                    );
        }

        /*
         * pageSize보다 한 개 더 조회해서
         * 다음 페이지 존재 여부를 판단한다.
         */
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

        /*
         * 다음 페이지가 존재하면
         * 현재 페이지 마지막 공지의
         * createdAt + id로 cursor를 생성한다.
         */
        if (hasNext && !page.isEmpty()) {

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
                spaceNoticeRepository
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

    /**
     * NOT-002
     * 공지 상세 조회 및 읽음 처리
     */
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
                notice.getSpace().getId(),
                currentUser.getId()
        );

        /*
         * 처음 읽은 경우 notice_reads 생성.
         *
         * 이미 읽은 공지라면 기존 값을 그대로 사용한다.
         */
        NoticeRead read =
                noticeReadRepository
                        .findByNoticeIdAndUserId(
                                noticeId,
                                currentUser.getId()
                        )
                        .orElseGet(
                                () ->
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
                        .map(
                                NoticeFileResponse::from
                        )
                        .toList();

        return new NoticeDetailResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getCreatedAt(),
                notice.getAuthor().getName(),
                notice.getViewCount(),
                true,
                read.getReadAt(),
                notice.getContent(),
                files
        );
    }

    /**
     * NOT-003
     * 공지 등록
     */
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
                        currentUser.getId()
                );

        SpaceNotice notice =
                SpaceNotice.create(
                        member.getSpace(),
                        currentUser,
                        request.title().trim(),
                        request.content().trim()
                );

        spaceNoticeRepository.save(
                notice
        );

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

    /**
     * NOT-004
     * 공지 수정
     */
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
                notice.getSpace().getId(),
                currentUser.getId()
        );

        notice.update(
                request.title().trim(),
                request.content().trim()
        );

        List<NoticeFile> existingFiles =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        );

        /*
         * retained_file_ids 규칙
         *
         * null
         * → 기존 첨부파일 전체 유지
         *
         * []
         * → 기존 첨부파일 전체 삭제
         *
         * [id1, id2]
         * → 해당 파일만 유지
         */
        if (request.retainedFileIds() != null) {

            Set<UUID> retainedIds =
                    new HashSet<>(
                            request.retainedFileIds()
                    );

            Set<UUID> existingIds =
                    existingFiles.stream()
                            .map(
                                    NoticeFile::getId
                            )
                            .collect(
                                    java.util.stream.Collectors.toSet()
                            );

            /*
             * 다른 공지의 fileId 등을 전달한 경우 방지
             */
            if (!existingIds.containsAll(
                    retainedIds
            )) {
                throw new BusinessException(
                        NoticeErrorCode.INVALID_RETAINED_FILE
                );
            }

            List<NoticeFile> filesToDelete =
                    existingFiles.stream()
                            .filter(
                                    file ->
                                            !retainedIds.contains(
                                                    file.getId()
                                            )
                            )
                            .toList();

            /*
             * 삭제 대상 파일은 S3에서도 제거
             */
            for (NoticeFile file : filesToDelete) {

                s3Service.deleteByUrlIfManaged(
                        file.getFileUrl()
                );
            }

            noticeFileRepository.deleteAll(
                    filesToDelete
            );
        }

        /*
         * 새 첨부파일 추가
         */
        uploadFiles(
                notice,
                newFiles
        );

        List<NoticeFileResponse> responseFiles =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        )
                        .stream()
                        .map(
                                NoticeFileResponse::from
                        )
                        .toList();

        return new NoticeUpdateResponse(
                notice.getId(),
                notice.getTitle(),
                notice.getContent(),
                responseFiles,
                notice.getUpdatedAt()
        );
    }

    /**
     * NOT-005
     * 공지 삭제
     */
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
                notice.getSpace().getId(),
                currentUser.getId()
        );

        List<NoticeFile> files =
                noticeFileRepository
                        .findAllByNoticeId(
                                noticeId
                        );

        /*
         * 첨부파일 S3 삭제
         */
        for (NoticeFile file : files) {

            s3Service.deleteByUrlIfManaged(
                    file.getFileUrl()
            );
        }

        /*
         * FK 관계 데이터 정리
         */
        noticeReadRepository
                .deleteAllByNoticeId(
                        noticeId
                );

        noticeFileRepository
                .deleteAllByNoticeId(
                        noticeId
                );

        spaceNoticeRepository.delete(
                notice
        );
    }

    /**
     * 공지 첨부파일 업로드
     */
    private void uploadFiles(
            SpaceNotice notice,
            List<MultipartFile> files
    ) {

        if (files == null || files.isEmpty()) {
            return;
        }

        /*
         * Swagger에서 Send empty value가 체크된 경우
         * 빈 MultipartFile이 넘어올 수 있으므로 제거한다.
         */
        List<MultipartFile> validFiles =
                files.stream()
                        .filter(
                                file ->
                                        file != null
                                                && !file.isEmpty()
                        )
                        .toList();

        if (validFiles.isEmpty()) {
            return;
        }

        List<S3UploadResult> uploadedFiles =
                s3Service.uploadAll(
                        validFiles,
                        "notices",
                        FileUploadType.NOTICE_ATTACHMENT
                );

        for (int i = 0;
             i < uploadedFiles.size();
             i++) {

            MultipartFile multipartFile =
                    validFiles.get(i);

            S3UploadResult uploadedFile =
                    uploadedFiles.get(i);

            String originalName =
                    multipartFile
                            .getOriginalFilename();

            /*
             * 파일명이 없는 경우 기본값 지정
             */
            if (originalName == null
                    || originalName.isBlank()) {

                originalName = "file";
            }

            NoticeFile noticeFile =
                    NoticeFile.create(
                            notice,
                            originalName,
                            uploadedFile.url()
                    );

            noticeFileRepository.save(
                    noticeFile
            );
        }
    }

    /**
     * 공지 조회
     */
    private SpaceNotice getNoticeEntity(
            UUID noticeId
    ) {

        return spaceNoticeRepository
                .findById(
                        noticeId
                )
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        NoticeErrorCode.NOTICE_NOT_FOUND
                                )
                );
    }

    /**
     * Space 참여자 검증
     */
    private SpaceMember requireApprovedMember(
            UUID spaceId,
            UUID userId
    ) {

        return spaceMemberRepository
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        userId,
                        SpaceMemberStatus.APPROVED
                )
                .orElseThrow(
                        () ->
                                new BusinessException(
                                        SpaceMemberErrorCode.SPACE_ACCESS_DENIED
                                )
                );
    }

    /**
     * 공지 관리 권한 검증
     *
     * PROFESSOR
     * → 허용
     *
     * ASSISTANT + NOTICE_MANAGE
     * → 허용
     *
     * 나머지
     * → 거부
     */
    private SpaceMember requireNoticeManager(
            UUID spaceId,
            UUID userId
    ) {

        SpaceMember member =
                requireApprovedMember(
                        spaceId,
                        userId
                );

        if (member.getRole()
                == SpaceMemberRole.PROFESSOR) {

            return member;
        }

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
                NoticeErrorCode.NOTICE_MANAGE_FORBIDDEN
        );
    }

    /**
     * size 보정
     */
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

    /**
     * 공지 목록용 본문 미리보기
     */
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

    /**
     * Cursor 내부 데이터
     */
    public record NoticeCursor(
            java.time.Instant createdAt,
            UUID id
    ) {
    }
}