package com.tikitaka.document.service;

import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.tikitaka.document.dto.response.DocumentRevisionDetailResponse;
import com.tikitaka.document.dto.response.RevisionPageResponse;
import com.tikitaka.document.dto.response.RevisionSourceSlideResponse;
import com.tikitaka.document.dto.response.SourcePdfUploadResponse;
import com.tikitaka.document.dto.request.RevisionOperationRequest;
import com.tikitaka.document.dto.request.RevisionPreviewVersionRequest;
import com.tikitaka.document.dto.response.RevisionOperationResponse;
import com.tikitaka.document.dto.response.RevisionUndoRedoResponse;
import com.tikitaka.document.dto.response.DocumentRevisionCancelResponse;
import com.tikitaka.document.dto.response.DocumentRevisionCompleteResponse;
import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.DocumentRevision;
import com.tikitaka.document.entity.RevisionOperationState;
import com.tikitaka.document.entity.RevisionOperation;
import com.tikitaka.document.entity.RevisionOperationType;
import com.tikitaka.document.entity.RevisionPageStatus;
import com.tikitaka.document.entity.RevisionPage;
import com.tikitaka.document.entity.RevisionSlide;
import com.tikitaka.document.entity.RevisionStatus;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.document.pdf.PdfProcessor;
import com.tikitaka.document.pdf.ProcessedPdf;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.DocumentRevisionRepository;
import com.tikitaka.document.repository.RevisionOperationRepository;
import com.tikitaka.document.repository.RevisionPageRepository;
import com.tikitaka.document.repository.RevisionSlideRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.s3.S3ObjectNames;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.s3.FileUploadType;
import com.tikitaka.global.s3.S3FileValidator;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentRevisionService {
    public record RevisionOpenResult(DocumentRevisionDetailResponse detail, boolean created) {}

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final RevisionPageRepository revisionPageRepository;
    private final RevisionSlideRepository revisionSlideRepository;
    private final RevisionOperationRepository revisionOperationRepository;
    private final SlideRepository slideRepository;
    private final SpaceMemberRepository memberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;
    private final S3FileValidator fileValidator;
    private final PdfProcessor pdfProcessor;
    private final DocumentStorage storage;
    private final DocumentRevisionCompletionWorker completionWorker;

    @Value("${document.revision.inactivity-timeout:PT2H}")
    private Duration inactivityTimeout;

    @Transactional
    public RevisionOpenResult createRevision(UUID documentId, User user) {
        Document document = document(documentId);
        requireManager(document, user);
        var active = revisionRepository.findFirstByDocumentIdAndStatusIn(
                documentId, List.of(RevisionStatus.EDITING, RevisionStatus.PROCESSING));
        if (active.isPresent()) {
            DocumentRevision revision = active.get();
            if (revision.getStatus() == RevisionStatus.PROCESSING) {
                throw new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE);
            }
            if (!revision.getEditor().getId().equals(user.getId())) {
                throw new BusinessException(DocumentErrorCode.REVISION_ALREADY_ACTIVE);
            }
            revision.resume();
            return new RevisionOpenResult(revisionDetail(revision, documentId), false);
        }
        DocumentRevision revision = revisionRepository.save(DocumentRevision.create(document, user));
        List<RevisionPage> pages = slideRepository.findAllByDocumentIdOrderByPageNumberAsc(documentId).stream()
                .map(slide -> RevisionPage.original(revision, slide.getPageNumber(), slide)).toList();
        revisionPageRepository.saveAll(pages);
        return new RevisionOpenResult(revisionDetail(revision, documentId), true);
    }

    @Transactional
    public SourcePdfUploadResponse uploadSourcePdf(UUID documentId, UUID revisionId, MultipartFile file, User user) {
        DocumentRevision revision = editableRevision(documentId, revisionId, user);
        if (revision.getSourcePdfKey() != null) throw new BusinessException(DocumentErrorCode.REVISION_SOURCE_PDF_ALREADY_EXISTS);
        fileValidator.validate(file, FileUploadType.LECTURE_DOCUMENT);
        ProcessedPdf pdf = pdfProcessor.process(file);
        String root = "documents/" + documentId + "/revisions/" + revisionId + "/source/" + UUID.randomUUID();
        String pdfKey = root + "/source.pdf";
        List<String> keys = new ArrayList<>();
        try {
            storage.put(pdfKey, pdf.originalBytes(), MediaType.APPLICATION_PDF_VALUE); keys.add(pdfKey);
            List<RevisionSlide> slides = new ArrayList<>();
            for (int i = 0; i < pdf.pageCount(); i++) {
                String key = root + "/slides/" + S3ObjectNames.imageFilename(revision.getDocument().getTitle(), "추가슬라이드_" + (i + 1), ".png");
                storage.put(key, pdf.pageThumbnails().get(i), MediaType.IMAGE_PNG_VALUE); keys.add(key);
                slides.add(RevisionSlide.create(revision, i + 1, key));
            }
            revision.updateSourcePdf(file.getOriginalFilename(), pdfKey, pdf.pageCount());
            List<RevisionSlide> saved = revisionSlideRepository.saveAll(slides);
            return new SourcePdfUploadResponse(revisionId, revision.getSourceFileName(), storage.presignedGetUrl(pdfKey), pdf.pageCount(),
                    saved.stream().map(s -> new RevisionSourceSlideResponse(s.getId(), s.getSourcePageNumber(), storage.presignedGetUrl(s.getThumbnailKey()))).toList());
        } catch (RuntimeException e) { keys.forEach(k -> { try { storage.delete(k); } catch (RuntimeException ignored) {} }); throw e; }
    }

    public DocumentRevisionDetailResponse getRevision(UUID documentId, UUID revisionId, User user) {
        DocumentRevision revision = revision(revisionId); verifyDocument(revision, documentId); requireManager(revision.getDocument(), user);
        return revisionDetail(revision, documentId);
    }

    private DocumentRevisionDetailResponse revisionDetail(DocumentRevision revision, UUID documentId) {
        UUID revisionId = revision.getId();
        List<RevisionSourceSlideResponse> revisionSlides = revisionSlideRepository
                .findAllByRevisionIdOrderBySourcePageNumberAsc(revisionId).stream()
                .map(slide -> new RevisionSourceSlideResponse(
                        slide.getId(), slide.getSourcePageNumber(), storage.presignedGetUrl(slide.getThumbnailKey())))
                .toList();
        List<RevisionPageResponse> pages = revisionPageRepository.findAllByRevisionIdOrderByPositionAsc(revisionId).stream()
                .map(p -> new RevisionPageResponse(p.getId(), p.getPosition(), p.getSourceType(), p.getStatus(), storage.presignedGetUrl(p.getThumbnailKey()))).toList();
        List<com.tikitaka.document.entity.RevisionOperation> operations = revisionOperationRepository.findAllByRevisionIdOrderBySequenceAsc(revisionId);
        Integer cursor = revision.getOperationCursorSequence();
        boolean undo = cursor != null;
        boolean redo = operations.stream().anyMatch(o -> o.getState() == RevisionOperationState.UNDONE && (cursor == null || o.getSequence() > cursor));
        String sourcePdfUrl = revision.getSourcePdfKey() == null ? null : storage.presignedGetUrl(revision.getSourcePdfKey());
        return new DocumentRevisionDetailResponse(
                revisionId,
                documentId,
                revision.getStatus(),
                revision.getBaseDocumentVersion(),
                revision.getPreviewVersion(),
                revision.getDocument().getTitle(),
                revision.getSourceFileName(),
                sourcePdfUrl,
                revision.getSourcePageCount(),
                revisionSlides,
                pages,
                undo,
                redo);
    }

    @Transactional
    public RevisionOperationResponse applyOperation(UUID documentId, UUID revisionId, RevisionOperationRequest request, User user) {
        DocumentRevision revision = lockedEditableRevision(documentId, revisionId, user);
        if (request.clientOperationId() == null || request.type() == null) throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        var duplicate = revisionOperationRepository.findByRevisionIdAndClientOperationId(revisionId, request.clientOperationId());
        if (duplicate.isPresent()) {
            if (!isSameRequest(duplicate.get(), request)) throw new BusinessException(DocumentErrorCode.REVISION_OPERATION_ID_CONFLICT);
            return operationResponse(revision, duplicate.get());
        }
        validatePreviewVersion(revision, request.basePreviewVersion());
        discardRedo(revision);
        int sequence = revisionOperationRepository.findFirstByRevisionIdOrderBySequenceDesc(revisionId).map(o -> o.getSequence() + 1).orElse(1);
        RevisionOperation operation;
        if (request.type() == RevisionOperationType.INSERT) operation = insert(revision, request, sequence);
        else if (request.type() == RevisionOperationType.DELETE) operation = delete(revision, request, sequence);
        else throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        revision.increasePreviewVersion();
        revision.moveOperationCursorTo(sequence);
        revisionOperationRepository.save(operation);
        return operationResponse(revision, operation);
    }

    @Transactional
    public RevisionUndoRedoResponse undo(UUID documentId, UUID revisionId, RevisionPreviewVersionRequest request, User user) {
        DocumentRevision revision = lockedEditableRevision(documentId, revisionId, user); validatePreviewVersion(revision, request.basePreviewVersion());
        if (revision.getOperationCursorSequence() == null) throw new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE);
        RevisionOperation operation = revisionOperationRepository.findByRevisionIdAndSequence(revisionId, revision.getOperationCursorSequence()).orElseThrow(() -> new BusinessException(DocumentErrorCode.REVISION_NOT_FOUND));
        applyInverse(operation); operation.undo();
        revision.moveOperationCursorTo(revisionOperationRepository.findFirstByRevisionIdAndStateAndSequenceLessThanOrderBySequenceDesc(revisionId, RevisionOperationState.APPLIED, operation.getSequence()).map(RevisionOperation::getSequence).orElse(null));
        revision.increasePreviewVersion();
        return undoRedoResponse(revision, operation);
    }

    @Transactional
    public RevisionUndoRedoResponse redo(UUID documentId, UUID revisionId, RevisionPreviewVersionRequest request, User user) {
        DocumentRevision revision = lockedEditableRevision(documentId, revisionId, user); validatePreviewVersion(revision, request.basePreviewVersion());
        int cursor = revision.getOperationCursorSequence() == null ? 0 : revision.getOperationCursorSequence();
        RevisionOperation operation = revisionOperationRepository.findFirstByRevisionIdAndStateAndSequenceGreaterThanOrderBySequenceAsc(revisionId, RevisionOperationState.UNDONE, cursor).orElseThrow(() -> new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE));
        applyForward(operation); operation.redo(); revision.moveOperationCursorTo(operation.getSequence()); revision.increasePreviewVersion();
        return undoRedoResponse(revision, operation);
    }

    @Transactional
    public DocumentRevisionCompleteResponse complete(UUID documentId, UUID revisionId, RevisionPreviewVersionRequest request, User user) {
        DocumentRevision revision = lockedEditableRevision(documentId, revisionId, user);
        validatePreviewVersion(revision, request.basePreviewVersion());
        if (!revision.getBaseDocumentVersion().equals(revision.getDocument().getVersion())) {
            throw new BusinessException(DocumentErrorCode.DOCUMENT_VERSION_CONFLICT);
        }
        revision.startProcessing();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { completionWorker.completeAsync(revision.getId()); }
        });
        return DocumentRevisionCompleteResponse.processing(revision);
    }

    @Transactional
    public DocumentRevisionCancelResponse cancel(UUID documentId, UUID revisionId, User user) {
        DocumentRevision revision = revisionRepository.findByIdForUpdate(revisionId).orElseThrow(() -> new BusinessException(DocumentErrorCode.REVISION_NOT_FOUND));
        verifyDocument(revision, documentId);
        if (!revision.getEditor().getId().equals(user.getId())) {
            SpaceMember member = memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                    revision.getDocument().getSpace().getId(), user.getId(), SpaceMemberStatus.APPROVED)
                    .orElseThrow(() -> new BusinessException(DocumentErrorCode.REVISION_ACCESS_DENIED));
            if (member.getRole() != SpaceMemberRole.PROFESSOR) {
                throw new BusinessException(DocumentErrorCode.REVISION_ACCESS_DENIED);
            }
        }
        if (revision.getStatus() != RevisionStatus.EDITING) throw new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE);
        cancelRevision(revision);
        return new DocumentRevisionCancelResponse(revisionId, revision.getStatus());
    }

    @Scheduled(fixedDelayString = "${document.revision.cleanup-fixed-delay:PT5M}")
    @Transactional
    public void cancelInactiveRevisions() {
        Instant updatedBefore = Instant.now().minus(inactivityTimeout);
        revisionRepository.findAllInactiveForUpdate(
                        RevisionStatus.EDITING,
                        updatedBefore
                )
                .forEach(this::cancelRevision);
    }


    private RevisionOperation insert(DocumentRevision revision, RevisionOperationRequest request, int sequence) {
        if (request.position() == null || request.revisionSlideIds() == null || request.revisionSlideIds().isEmpty()) throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        List<RevisionPage> existing = revisionPageRepository.findAllByRevisionIdOrderByPositionAsc(revision.getId());
        if (request.position() < 1 || request.position() > existing.size() + 1) throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        List<RevisionSlide> source = revisionSlideRepository.findAllById(request.revisionSlideIds());
        if (source.size() != request.revisionSlideIds().size() || source.stream().anyMatch(s -> !s.getRevision().getId().equals(revision.getId()))) throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        existing.stream().filter(p -> p.getPosition() >= request.position()).forEach(p -> p.changePosition(p.getPosition() + 1000)); revisionPageRepository.flush();
        existing.stream().filter(p -> p.getPosition() >= request.position() + 1000).forEach(p -> p.changePosition(p.getPosition() - 1000 + source.size()));
        List<RevisionPage> created = new ArrayList<>();
        for (int i = 0; i < source.size(); i++) created.add(RevisionPage.revision(revision, request.position() + i, source.get(i)));
        revisionPageRepository.saveAll(created); revisionPageRepository.flush();
        List<String> ids = created.stream().map(p -> p.getId().toString()).toList();
        return RevisionOperation.create(revision, request.clientOperationId(), sequence, RevisionOperationType.INSERT,
                Map.of("page_ids", ids, "revision_slide_ids", request.revisionSlideIds().stream().map(UUID::toString).toList(), "position", request.position()),
                Map.of("page_ids", ids), revision.getPreviewVersion() + 1);
    }

    private RevisionOperation delete(DocumentRevision revision, RevisionOperationRequest request, int sequence) {
        if (request.pageIds() == null || request.pageIds().isEmpty()) throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        List<RevisionPage> pages = revisionPageRepository.findAllByRevisionIdOrderByPositionAsc(revision.getId()).stream().filter(p -> request.pageIds().contains(p.getId())).toList();
        if (pages.size() != request.pageIds().size() || pages.stream().anyMatch(p -> p.getStatus() != RevisionPageStatus.ACTIVE)) throw new BusinessException(DocumentErrorCode.INVALID_PDF_FILE);
        pages.forEach(RevisionPage::markDeletePending);
        List<String> ids = pages.stream().map(p -> p.getId().toString()).toList();
        return RevisionOperation.create(revision, request.clientOperationId(), sequence, RevisionOperationType.DELETE, Map.of("page_ids", ids), Map.of("page_ids", ids), revision.getPreviewVersion() + 1);
    }

    private void applyForward(RevisionOperation o) { pages(o).forEach(p -> { if (o.getType() == RevisionOperationType.INSERT || o.getType() == RevisionOperationType.DELETE) p.markDeletePending(); }); if (o.getType() == RevisionOperationType.INSERT) pages(o).forEach(RevisionPage::reactivate); }
    private void applyInverse(RevisionOperation o) { pages(o).forEach(p -> { if (o.getType() == RevisionOperationType.INSERT) p.markDeletePending(); else p.reactivate(); }); }
    @SuppressWarnings("unchecked") private boolean isSameRequest(RevisionOperation o, RevisionOperationRequest r) {
        if (o.getType() != r.type()) return false;
        if (r.type() == RevisionOperationType.INSERT) {
            return java.util.Objects.equals(o.getPayload().get("position"), r.position())
                    && java.util.Objects.equals(o.getPayload().get("revision_slide_ids"), r.revisionSlideIds() == null ? null : r.revisionSlideIds().stream().map(UUID::toString).toList());
        }
        List<String> stored = (List<String>) o.getPayload().get("page_ids");
        return r.pageIds() != null && new java.util.HashSet<>(stored).equals(new java.util.HashSet<>(r.pageIds().stream().map(UUID::toString).toList()));
    }
    @SuppressWarnings("unchecked") private List<RevisionPage> pages(RevisionOperation o) { List<String> ids = (List<String>) o.getPayload().get("page_ids"); return revisionPageRepository.findAllById(ids.stream().map(UUID::fromString).toList()); }
    private void discardRedo(DocumentRevision r) { int c = r.getOperationCursorSequence() == null ? 0 : r.getOperationCursorSequence(); revisionOperationRepository.findAllByRevisionIdAndStateAndSequenceGreaterThanOrderBySequenceAsc(r.getId(), RevisionOperationState.UNDONE, c).forEach(RevisionOperation::discard); }
    private void validatePreviewVersion(DocumentRevision r, Integer v) { if (v == null || !v.equals(r.getPreviewVersion())) throw new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE); }
    private RevisionOperationResponse operationResponse(DocumentRevision r, RevisionOperation o) { return new RevisionOperationResponse(o.getId(), o.getSequence(), r.getPreviewVersion(), r.getOperationCursorSequence() != null, revisionOperationRepository.findAllByRevisionIdOrderBySequenceAsc(r.getId()).stream().anyMatch(x -> x.getState() == RevisionOperationState.UNDONE)); }
    private RevisionUndoRedoResponse undoRedoResponse(DocumentRevision r, RevisionOperation o) { RevisionOperationResponse x = operationResponse(r, o); return new RevisionUndoRedoResponse(r.getId(), o.getId(), x.previewVersion(), x.canUndo(), x.canRedo()); }
    private DocumentRevision lockedEditableRevision(UUID d, UUID r, User u) { DocumentRevision x = revisionRepository.findByIdForUpdate(r).orElseThrow(() -> new BusinessException(DocumentErrorCode.REVISION_NOT_FOUND)); verifyDocument(x, d); if (!x.getEditor().getId().equals(u.getId())) throw new BusinessException(DocumentErrorCode.REVISION_ACCESS_DENIED); if (x.getStatus() != RevisionStatus.EDITING) throw new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE); return x; }

    private DocumentRevision editableRevision(UUID documentId, UUID revisionId, User user) {
        DocumentRevision r = revision(revisionId); verifyDocument(r, documentId);
        if (!r.getEditor().getId().equals(user.getId())) throw new BusinessException(DocumentErrorCode.REVISION_ACCESS_DENIED);
        if (r.getStatus() != RevisionStatus.EDITING) throw new BusinessException(DocumentErrorCode.REVISION_NOT_EDITABLE);
        return r;
    }
    private DocumentRevision revision(UUID id) { return revisionRepository.findById(id).orElseThrow(() -> new BusinessException(DocumentErrorCode.REVISION_NOT_FOUND)); }
    private Document document(UUID id) { return documentRepository.findById(id).orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND)); }
    private void verifyDocument(DocumentRevision r, UUID id) { if (!r.getDocument().getId().equals(id)) throw new BusinessException(DocumentErrorCode.REVISION_NOT_FOUND); }
    private void requireManager(Document d, User u) { SpaceMember m = memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(d.getSpace().getId(), u.getId(), SpaceMemberStatus.APPROVED).orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_ACCESS_DENIED)); if (m.getRole() != SpaceMemberRole.PROFESSOR && (m.getRole() != SpaceMemberRole.ASSISTANT || !permissionRepository.existsBySpaceMemberIdAndPermission(m.getId(), PermissionType.LECTURE_MATERIAL_MANAGE))) throw new BusinessException(DocumentErrorCode.DOCUMENT_ACCESS_DENIED); }

    private void cancelRevision(DocumentRevision revision) {
        List<String> keys = new ArrayList<>();
        if (revision.getSourcePdfKey() != null) {
            keys.add(revision.getSourcePdfKey());
        }
        revisionSlideRepository.findAllByRevisionIdOrderBySourcePageNumberAsc(revision.getId())
                .forEach(slide -> keys.add(slide.getThumbnailKey()));
        revisionPageRepository.deleteAllByRevisionId(revision.getId());
        revisionOperationRepository.deleteAllByRevisionId(revision.getId());
        revisionSlideRepository.deleteAllByRevisionId(revision.getId());
        revision.cancel();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                keys.forEach(key -> {
                    try {
                        storage.delete(key);
                    } catch (RuntimeException ignored) {
                        // DB 취소는 완료됐으므로 S3 정리 실패는 재시도 대상으로 남긴다.
                    }
                });
            }
        });
    }
}
