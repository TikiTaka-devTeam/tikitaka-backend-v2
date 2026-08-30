package com.tikitaka.systemnotice.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.systemnotice.dto.response.SystemNoticeDetailResponse;
import com.tikitaka.systemnotice.dto.response.SystemNoticeListItemResponse;
import com.tikitaka.systemnotice.dto.response.SystemNoticeListResponse;
import com.tikitaka.systemnotice.entity.SystemNotice;
import com.tikitaka.systemnotice.entity.SystemNoticeRead;
import com.tikitaka.systemnotice.exception.SystemNoticeErrorCode;
import com.tikitaka.systemnotice.repository.SystemNoticeReadRepository;
import com.tikitaka.systemnotice.repository.SystemNoticeRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SystemNoticeService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final int PREVIEW_LENGTH = 50;

    private final SystemNoticeRepository systemNoticeRepository;
    private final SystemNoticeReadRepository systemNoticeReadRepository;
    private final CursorCodec cursorCodec;

    /**
     * SYS-NOT-001
     * 시스템 공지사항 목록 조회
     */
    public SystemNoticeListResponse getSystemNotices(
            String cursor,
            int size,
            User currentUser
    ) {

        int pageSize = normalizeSize(size);

        SystemNoticeCursor decoded =
                cursorCodec.decodeOrNull(
                        cursor,
                        SystemNoticeCursor.class
                );

        List<SystemNotice> fetched;

        /*
         * 첫 페이지에서는 null cursor 값을
         * JPQL 파라미터로 넘기지 않는다.
         */
        if (decoded == null) {

            fetched =
                    systemNoticeRepository.findFirstPage(
                            PageRequest.of(
                                    0,
                                    pageSize + 1
                            )
                    );

        } else {

            fetched =
                    systemNoticeRepository.findNextPage(
                            decoded.createdAt(),
                            decoded.id(),
                            PageRequest.of(
                                    0,
                                    pageSize + 1
                            )
                    );
        }

        /*
         * 한 개 더 조회해서 다음 페이지 존재 여부 확인
         */
        boolean hasNext =
                fetched.size() > pageSize;

        List<SystemNotice> page =
                hasNext
                        ? fetched.subList(
                                0,
                                pageSize
                        )
                        : fetched;

        List<SystemNoticeListItemResponse> systemNotices =
                page.stream()
                        .map(systemNotice ->
                                new SystemNoticeListItemResponse(
                                        systemNotice.getId(),
                                        systemNotice.getTitle(),
                                        preview(
                                                systemNotice.getContent()
                                        ),
                                        systemNotice.isImportant(),
                                        systemNoticeReadRepository
                                                .existsBySystemNoticeIdAndUserId(
                                                        systemNotice.getId(),
                                                        currentUser.getId()
                                                ),
                                        systemNotice.getCreatedAt()
                                )
                        )
                        .toList();

        String nextCursor = null;

        if (hasNext && !page.isEmpty()) {

            SystemNotice last =
                    page.get(
                            page.size() - 1
                    );

            nextCursor =
                    cursorCodec.encode(
                            new SystemNoticeCursor(
                                    last.getCreatedAt(),
                                    last.getId()
                            )
                    );
        }

        long totalCount =
                systemNoticeRepository.count();

        long unreadCount =
                systemNoticeReadRepository.countUnread(
                        currentUser.getId()
                );

        return new SystemNoticeListResponse(
                totalCount,
                unreadCount,
                systemNotices,
                nextCursor,
                hasNext
        );
    }

    /**
     * SYS-NOT-002
     * 시스템 공지사항 상세 조회 및 읽음 처리
     */
    @Transactional
    public SystemNoticeDetailResponse getSystemNotice(
            UUID systemNoticeId,
            User currentUser
    ) {

        SystemNotice systemNotice =
                systemNoticeRepository
                        .findById(systemNoticeId)
                        .orElseThrow(
                                () ->
                                        new BusinessException(
                                                SystemNoticeErrorCode
                                                        .SYSTEM_NOTICE_NOT_FOUND
                                        )
                        );

        SystemNoticeRead read =
                systemNoticeReadRepository
                        .findBySystemNoticeIdAndUserId(
                                systemNoticeId,
                                currentUser.getId()
                        )
                        .orElseGet(
                                () ->
                                        systemNoticeReadRepository.save(
                                                SystemNoticeRead.create(
                                                        systemNotice,
                                                        currentUser
                                                )
                                        )
                        );

        return new SystemNoticeDetailResponse(
                systemNotice.getId(),
                systemNotice.getTitle(),
                systemNotice.getContent(),
                systemNotice.isImportant(),
                true,
                read.getReadAt(),
                systemNotice.getCreatedAt()
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

    public record SystemNoticeCursor(
            java.time.Instant createdAt,
            UUID id
    ) {
    }
}