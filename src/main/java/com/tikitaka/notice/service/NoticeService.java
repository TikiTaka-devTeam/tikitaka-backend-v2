package com.tikitaka.notice.service;

import java.time.Instant;
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

    public NoticeListResponse getNotices(UUID spaceId, String cursor, int size, User currentUser) {
        requireApprovedMember(spaceId, currentUser.getId());
        int pageSize = normalizeSize(size);
        NoticeCursor decoded = cursorCodec.decodeOrNull(cursor, NoticeCursor.class);
        Instant createdAt = decoded == null ? null : decoded.createdAt();
        UUID id = decoded == null ? null : decoded.id();
        List<SpaceNotice> fetched = spaceNoticeRepository.findPage(
                spaceId, createdAt, id, PageRequest.of(0, pageSize + 1));
        boolean hasNext = fetched.size() > pageSize;
        List<SpaceNotice> page = hasNext ? fetched.subList(0, pageSize) : fetched;

        List<NoticeListItemResponse> notices = page.stream()
                .map(n -> new NoticeListItemResponse(
                        n.getId(), n.getTitle(), preview(n.getContent()), n.getCreatedAt(),
                        noticeReadRepository.existsByNoticeIdAndUserId(n.getId(), currentUser.getId())))
                .toList();

        String nextCursor = null;
        if (hasNext && !page.isEmpty()) {
            SpaceNotice last = page.get(page.size() - 1);
            nextCursor = cursorCodec.encode(new NoticeCursor(last.getCreatedAt(), last.getId()));
        }

        return new NoticeListResponse(
                spaceNoticeRepository.countBySpaceId(spaceId),
                noticeReadRepository.countUnread(spaceId, currentUser.getId()),
                notices, nextCursor, hasNext);
    }

    @Transactional
    public NoticeDetailResponse getNotice(UUID noticeId, User currentUser) {
        SpaceNotice notice = getNoticeEntity(noticeId);
        requireApprovedMember(notice.getSpace().getId(), currentUser.getId());
        NoticeRead read = noticeReadRepository.findByNoticeIdAndUserId(noticeId, currentUser.getId())
                .orElseGet(() -> noticeReadRepository.save(NoticeRead.create(notice, currentUser)));
        notice.increaseViewCount();
        List<NoticeFileResponse> files = noticeFileRepository.findAllByNoticeId(noticeId).stream()
                .map(NoticeFileResponse::from).toList();
        return new NoticeDetailResponse(
                notice.getId(), notice.getTitle(), notice.getCreatedAt(), notice.getAuthor().getName(),
                notice.getViewCount(), true, read.getReadAt(), notice.getContent(), files);
    }

    @Transactional
    public NoticeCreateResponse createNotice(UUID spaceId, NoticeCreateRequest request,
            List<MultipartFile> files, User currentUser) {
        SpaceMember member = requireNoticeManager(spaceId, currentUser.getId());
        SpaceNotice notice = spaceNoticeRepository.save(
                SpaceNotice.create(member.getSpace(), currentUser, request.title().trim(), request.content().trim()));
        uploadFiles(notice, files);
        return new NoticeCreateResponse(notice.getId(), notice.getTitle(), notice.getCreatedAt());
    }

    @Transactional
    public NoticeUpdateResponse updateNotice(UUID noticeId, NoticeUpdateRequest request,
            List<MultipartFile> newFiles, User currentUser) {
        SpaceNotice notice = getNoticeEntity(noticeId);
        requireNoticeManager(notice.getSpace().getId(), currentUser.getId());
        notice.update(request.title().trim(), request.content().trim());

        List<NoticeFile> existing = noticeFileRepository.findAllByNoticeId(noticeId);
        if (request.retainedFileIds() != null) {
            Set<UUID> retained = new HashSet<>(request.retainedFileIds());
            Set<UUID> existingIds = existing.stream().map(NoticeFile::getId).collect(java.util.stream.Collectors.toSet());
            if (!existingIds.containsAll(retained)) {
                throw new BusinessException(NoticeErrorCode.INVALID_RETAINED_FILE);
            }
            List<NoticeFile> toDelete = existing.stream().filter(f -> !retained.contains(f.getId())).toList();
            for (NoticeFile file : toDelete) {
                s3Service.deleteByUrlIfManaged(file.getFileUrl());
            }
            noticeFileRepository.deleteAll(toDelete);
        }
        uploadFiles(notice, newFiles);
        List<NoticeFileResponse> responseFiles = noticeFileRepository.findAllByNoticeId(noticeId).stream()
                .map(NoticeFileResponse::from).toList();
        return new NoticeUpdateResponse(notice.getId(), notice.getTitle(), notice.getContent(),
                responseFiles, notice.getUpdatedAt());
    }

    @Transactional
    public void deleteNotice(UUID noticeId, User currentUser) {
        SpaceNotice notice = getNoticeEntity(noticeId);
        requireNoticeManager(notice.getSpace().getId(), currentUser.getId());
        List<NoticeFile> files = noticeFileRepository.findAllByNoticeId(noticeId);
        for (NoticeFile file : files) {
            s3Service.deleteByUrlIfManaged(file.getFileUrl());
        }
        noticeReadRepository.deleteAllByNoticeId(noticeId);
        noticeFileRepository.deleteAllByNoticeId(noticeId);
        spaceNoticeRepository.delete(notice);
    }

    private void uploadFiles(SpaceNotice notice, List<MultipartFile> files) {
        if (files == null || files.isEmpty()) return;
        List<S3UploadResult> uploaded = s3Service.uploadAll(files, "notices", FileUploadType.NOTICE_ATTACHMENT);
        for (int i = 0; i < uploaded.size(); i++) {
            String originalName = files.get(i).getOriginalFilename();
            S3UploadResult result = uploaded.get(i);
            noticeFileRepository.save(NoticeFile.create(notice, originalName == null ? "file" : originalName, result.url()));
        }
    }

    private SpaceNotice getNoticeEntity(UUID noticeId) {
        return spaceNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(NoticeErrorCode.NOTICE_NOT_FOUND));
    }

    private SpaceMember requireApprovedMember(UUID spaceId, UUID userId) {
        return spaceMemberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                spaceId, userId, SpaceMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(SpaceMemberErrorCode.SPACE_ACCESS_DENIED));
    }

    private SpaceMember requireNoticeManager(UUID spaceId, UUID userId) {
        SpaceMember member = requireApprovedMember(spaceId, userId);
        if (member.getRole() == SpaceMemberRole.PROFESSOR) return member;
        if (member.getRole() == SpaceMemberRole.ASSISTANT
                && permissionRepository.existsBySpaceMemberIdAndPermission(member.getId(), PermissionType.NOTICE_MANAGE)) {
            return member;
        }
        throw new BusinessException(NoticeErrorCode.NOTICE_MANAGE_FORBIDDEN);
    }

    private int normalizeSize(int size) {
        if (size <= 0) return DEFAULT_SIZE;
        return Math.min(size, MAX_SIZE);
    }

    private String preview(String content) {
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= PREVIEW_LENGTH ? normalized : normalized.substring(0, PREVIEW_LENGTH) + "...";
    }

    public record NoticeCursor(Instant createdAt, UUID id) {}
}
