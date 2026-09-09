package com.tikitaka.document.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.dto.request.RevisionOperationRequest;
import com.tikitaka.document.dto.request.RevisionPreviewVersionRequest;
import com.tikitaka.document.dto.response.DocumentRevisionCancelResponse;
import com.tikitaka.document.dto.response.DocumentRevisionCompleteResponse;
import com.tikitaka.document.dto.response.DocumentRevisionCreateResponse;
import com.tikitaka.document.dto.response.DocumentRevisionDetailResponse;
import com.tikitaka.document.dto.response.DocumentSlidesResponse;
import com.tikitaka.document.dto.response.RevisionOperationResponse;
import com.tikitaka.document.dto.response.RevisionUndoRedoResponse;
import com.tikitaka.document.dto.response.SourcePdfUploadResponse;
import com.tikitaka.document.service.DocumentRevisionService;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Document Revision", description = "강의자료 수정 세션 및 페이지 편집 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DocumentRevisionController {
    private final DocumentRevisionService documentRevisionService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "MAT-006 강의자료 수정 세션 생성", description = "실제 Slide를 기준으로 편집용 RevisionPage를 생성합니다.")
    @PostMapping("/api/v1/documents/{documentId}/revisions")
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentRevisionCreateResponse createRevision(@PathVariable UUID documentId, Authentication authentication) {
        return documentRevisionService.createRevision(documentId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-007 삽입용 PDF 업로드", description = "수정 세션에 삽입할 PDF와 페이지별 미리보기 썸네일을 생성합니다.")
    @PostMapping(value = "/api/v1/documents/{documentId}/revisions/{revisionId}/source-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SourcePdfUploadResponse uploadSourcePdf(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            @RequestPart("file") MultipartFile file, Authentication authentication) {
        return documentRevisionService.uploadSourcePdf(documentId, revisionId, file, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-005 실제 강의자료 페이지 목록 조회", description = "수정 세션의 임시 상태를 반영하지 않은 실제 Slide 목록을 조회합니다.")
    @GetMapping("/api/v1/documents/{documentId}/slides")
    public DocumentSlidesResponse getSlides(@PathVariable UUID documentId, Authentication authentication) {
        return documentRevisionService.getSlides(documentId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-008 수정 세션 상태 조회", description = "현재 RevisionPage 구성과 Undo/Redo 가능 여부를 조회합니다.")
    @GetMapping("/api/v1/documents/{documentId}/revisions/{revisionId}")
    public DocumentRevisionDetailResponse getRevision(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            Authentication authentication) {
        return documentRevisionService.getRevision(documentId, revisionId, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-009 페이지 편집 작업 등록", description = "INSERT 또는 DELETE 요청 한 건을 하나의 Undo/Redo 작업 단위로 등록합니다.")
    @PostMapping("/api/v1/documents/{documentId}/revisions/{revisionId}/operations")
    public RevisionOperationResponse applyRevisionOperation(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            @RequestBody RevisionOperationRequest request, Authentication authentication) {
        return documentRevisionService.applyOperation(documentId, revisionId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-010 마지막 편집 작업 Undo", description = "operation cursor가 가리키는 최신 작업 한 건을 취소합니다.")
    @PostMapping("/api/v1/documents/{documentId}/revisions/{revisionId}/undo")
    public RevisionUndoRedoResponse undoRevisionOperation(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            @RequestBody RevisionPreviewVersionRequest request, Authentication authentication) {
        return documentRevisionService.undo(documentId, revisionId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-011 편집 작업 Redo", description = "operation cursor 다음의 가장 이른 취소 작업 한 건을 다시 적용합니다.")
    @PostMapping("/api/v1/documents/{documentId}/revisions/{revisionId}/redo")
    public RevisionUndoRedoResponse redoRevisionOperation(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            @RequestBody RevisionPreviewVersionRequest request, Authentication authentication) {
        return documentRevisionService.redo(documentId, revisionId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-012 강의자료 수정 완료", description = "최종 RevisionPage 상태를 비동기로 실제 PDF와 Slide 구성에 반영합니다.")
    @PostMapping("/api/v1/documents/{documentId}/revisions/{revisionId}/complete")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentRevisionCompleteResponse completeRevision(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            @RequestBody RevisionPreviewVersionRequest request, Authentication authentication) {
        return documentRevisionService.complete(documentId, revisionId, request, currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-013 수정 세션 삭제", description = "수정 세션과 임시 파일을 정리하며 실제 강의자료에는 변경을 반영하지 않습니다.")
    @DeleteMapping("/api/v1/documents/{documentId}/revisions/{revisionId}")
    public DocumentRevisionCancelResponse cancelRevision(@PathVariable UUID documentId, @PathVariable UUID revisionId,
            Authentication authentication) {
        return documentRevisionService.cancel(documentId, revisionId, currentUserResolver.resolve(authentication));
    }
}
