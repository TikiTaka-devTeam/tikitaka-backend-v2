package com.tikitaka.document.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.dto.response.DocumentCreateResponse;
import com.tikitaka.document.dto.response.DocumentDownloadResponse;
import com.tikitaka.document.dto.response.DocumentListItemResponse;
import com.tikitaka.document.dto.response.DocumentSlidesResponse;
import com.tikitaka.document.service.DocumentService;
import com.tikitaka.global.config.OpenApiConfig;
import com.tikitaka.global.security.CurrentUserResolver;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "Document", description = "강의자료 등록·조회·삭제·다운로드 API")
@RestController
@RequiredArgsConstructor
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
public class DocumentController {
    private final DocumentService documentService;
    private final CurrentUserResolver currentUserResolver;

    @Operation(summary = "MAT-001 강의자료 목록 조회")
    @GetMapping("/api/v1/spaces/{spaceId}/documents")
    public List<DocumentListItemResponse> getDocuments(
            @PathVariable UUID spaceId,
            Authentication authentication
    ) {
        return documentService.getDocuments(
                spaceId,
                currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-002 PDF 강의자료 등록")
    @PostMapping(
            value = "/api/v1/spaces/{spaceId}/documents",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public DocumentCreateResponse createDocument(
            @PathVariable UUID spaceId,
            @RequestParam String title,
            @RequestPart("file") MultipartFile file,
            Authentication authentication
    ) {
        return documentService.createDocument(
                spaceId,
                title,
                file,
                currentUserResolver.resolve(authentication));
    }
    @DeleteMapping("/api/v1/documents/{documentId}")
    @Operation(summary = "MAT-003 강의자료 영구 삭제")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteDocument(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        documentService.deleteDocument(
                documentId,
                currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-004 강의자료 PDF 다운로드")
    @GetMapping("/api/v1/documents/{documentId}/download")
    public DocumentDownloadResponse downloadDocument(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return documentService.downloadDocument(
                documentId,
                currentUserResolver.resolve(authentication));
    }

    @Operation(summary = "MAT-005 실제 강의자료 페이지 목록 조회", description = "수정 세션의 임시 상태를 반영하지 않은 실제 Slide 목록을 조회합니다.")
    @GetMapping("/api/v1/documents/{documentId}/slides")
    public DocumentSlidesResponse getSlides(
            @PathVariable UUID documentId,
            Authentication authentication
    ) {
        return documentService.getSlides(
                documentId,
                currentUserResolver.resolve(authentication));
    }

}
