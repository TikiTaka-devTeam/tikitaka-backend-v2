package com.tikitaka.note.service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.exception.DocumentErrorCode;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.global.exception.CommonErrorCode;
import com.tikitaka.note.dto.request.StrokeSyncRequest;
import com.tikitaka.note.dto.request.FixerRequest;
import com.tikitaka.note.dto.request.StrokeSyncRequest.Operation;
import com.tikitaka.note.dto.request.StrokeSyncRequest.Type;
import com.tikitaka.note.dto.response.*;
import com.tikitaka.note.dto.response.StrokeSyncResponse.CreatedStroke;
import com.tikitaka.note.entity.*;
import com.tikitaka.note.exception.NoteErrorCode;
import com.tikitaka.note.repository.*;
import com.tikitaka.space.entity.*;
import com.tikitaka.space.repository.*;
import com.tikitaka.user.entity.User;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NoteService {
    private final SlideRepository slideRepository;
    private final SpaceMemberRepository memberRepository;
    private final SpaceMemberPermissionRepository permissionRepository;
    private final PrivateLayerRepository privateLayers;
    private final SharedLayerRepository sharedLayers;
    private final PrivateStrokeRepository privateStrokes;
    private final SharedStrokeRepository sharedStrokes;
    private final PrivateStrokeOperationRepository privateOperations;
    private final SharedStrokeOperationRepository sharedOperations;
    private final Validator validator;
    private final FixerRepository fixers;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StrokeLayerResponse getPrivate(UUID slideId, User user) {
        requireAccess(slideId, user, false, false);
        return privateLayers.findForRead(slideId, user.getId())
                .map(l -> new StrokeLayerResponse(slideId, l.getVersion(), privateStrokes
                        .findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAscIdAsc(l.getId())
                        .stream().map(StrokeResponse::of).toList()))
                .orElseGet(() -> new StrokeLayerResponse(slideId, 0, List.of()));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public StrokeLayerResponse getShared(UUID slideId, User user) {
        requireAccess(slideId, user, true, false);
        return sharedLayers.findForRead(slideId)
                .map(l -> new StrokeLayerResponse(slideId, l.getVersion(), sharedStrokes
                        .findAllByLayerIdAndDeletedFalseOrderByStrokeOrderAscIdAsc(l.getId())
                        .stream().map(StrokeResponse::of).toList()))
                .orElseGet(() -> new StrokeLayerResponse(slideId, 0, List.of()));
    }

    @Transactional
    public StrokeSyncResponse syncPrivate(UUID slideId, StrokeSyncRequest request, User user) {
        requireAccess(slideId, user, false, true);
        validate(request);
        privateLayers.ensureExists(slideId, user.getId());
        PrivateLayer layer = privateLayers.findForUpdate(slideId, user.getId()).orElseThrow();
        return sync(slideId, request, false, layer.getId(), layer.getVersion(), layer::increaseVersion);
    }

    @Transactional
    public StrokeSyncResponse syncShared(UUID slideId, StrokeSyncRequest request, User user) {
        requireAccess(slideId, user, true, true);
        validate(request);
        sharedLayers.ensureExists(slideId);
        SharedLayer layer = sharedLayers.findForUpdate(slideId).orElseThrow();
        return sync(slideId, request, true, layer.getId(), layer.getVersion(), layer::increaseVersion);
    }

    private StrokeSyncResponse sync(UUID slideId, StrokeSyncRequest request, boolean shared,
            UUID layerId, int version, Runnable increaseVersion) {
        List<UUID> ids = request.operations().stream().map(Operation::clientOperationId).toList();
        List<? extends StrokeOperationRecord> records = shared
                ? sharedOperations.findAllByLayerIdAndClientOperationIdIn(layerId, ids)
                : privateOperations.findAllByLayerIdAndClientOperationIdIn(layerId, ids);
        Map<UUID, StrokeOperationRecord> existing = records.stream().collect(Collectors.toMap(
                StrokeOperationRecord::getClientOperationId, Function.identity()));
        for (Operation operation : request.operations()) {
            StrokeOperationRecord record = existing.get(operation.clientOperationId());
            if (record != null && !record.getRequestPayload().equals(operation.payload())) {
                throw new BusinessException(NoteErrorCode.NOTE_OPERATION_ID_CONFLICT);
            }
        }
        Set<UUID> operationIdsToApply = new HashSet<>();
        Set<UUID> deleteTargets = new HashSet<>();
        for (Operation operation : request.operations()) {
            if (existing.containsKey(operation.clientOperationId())) continue;
            if (operation.type() == Type.DELETE
                    && (isDeleted(operation.strokeId(), layerId, shared)
                    || !deleteTargets.add(operation.strokeId()))) {
                continue;
            }
            operationIdsToApply.add(operation.clientOperationId());
        }
        int applied = operationIdsToApply.size();
        if (applied > 0 && request.baseVersion() != version) {
            throw new BusinessException(NoteErrorCode.NOTE_VERSION_CONFLICT);
        }
        if (applied > 0 && version == Integer.MAX_VALUE) {
            throw new BusinessException(NoteErrorCode.NOTE_VERSION_CONFLICT);
        }
        int resultVersion = applied > 0 ? version + 1 : version;
        List<CreatedStroke> created = new ArrayList<>();
        for (Operation operation : request.operations()) {
            StrokeOperationRecord prior = existing.get(operation.clientOperationId());
            UUID strokeId;
            if (prior != null) {
                strokeId = prior.getStrokeId();
            } else {
                if (!operationIdsToApply.contains(operation.clientOperationId())) continue;
                strokeId = apply(operation, layerId, shared);
                if (shared) {
                    sharedOperations.saveAndFlush(new SharedStrokeOperation(layerId,
                            operation.clientOperationId(), operation.payload(), strokeId, resultVersion));
                } else {
                    privateOperations.saveAndFlush(new PrivateStrokeOperation(layerId,
                            operation.clientOperationId(), operation.payload(), strokeId, resultVersion));
                }
            }
            if (operation.type() == Type.CREATE) {
                created.add(new CreatedStroke(operation.stroke().clientStrokeId(), strokeId));
            }
        }
        if (applied > 0) increaseVersion.run();
        return new StrokeSyncResponse(slideId, resultVersion, applied, List.copyOf(created));
    }

    private boolean isDeleted(UUID strokeId, UUID layerId, boolean shared) {
        if (shared) {
            return sharedStrokes.findByIdAndLayerId(strokeId, layerId)
                    .map(SharedStroke::isDeleted)
                    .orElseThrow(() -> new BusinessException(NoteErrorCode.NOTE_STROKE_NOT_FOUND));
        }
        return privateStrokes.findByIdAndLayerId(strokeId, layerId)
                .map(PrivateStroke::isDeleted)
                .orElseThrow(() -> new BusinessException(NoteErrorCode.NOTE_STROKE_NOT_FOUND));
    }

    private UUID apply(Operation operation, UUID layerId, boolean shared) {
        if (operation.type() == Type.DELETE) {
            if (shared) {
                sharedStrokes.findByIdAndLayerId(operation.strokeId(), layerId)
                        .orElseThrow(() -> new BusinessException(NoteErrorCode.NOTE_STROKE_NOT_FOUND)).delete();
            } else {
                privateStrokes.findByIdAndLayerId(operation.strokeId(), layerId)
                        .orElseThrow(() -> new BusinessException(NoteErrorCode.NOTE_STROKE_NOT_FOUND)).delete();
            }
            return operation.strokeId();
        }
        StrokeSyncRequest.Stroke stroke = operation.stroke();
        boolean used = shared
                ? sharedOperations.existsByLayerIdAndClientStrokeId(layerId, stroke.clientStrokeId())
                : privateOperations.existsByLayerIdAndClientStrokeId(layerId, stroke.clientStrokeId());
        if (used) throw new BusinessException(NoteErrorCode.NOTE_CLIENT_STROKE_ID_CONFLICT);
        List<Map<String, Double>> points = stroke.points().stream().map(p ->
                Map.of("x_ratio", p.xRatio(), "y_ratio", p.yRatio())).toList();
        if (shared) {
            return sharedStrokes.saveAndFlush(SharedStroke.create(sharedLayers.getReferenceById(layerId),
                    stroke.tool(), points, null, stroke.color(), stroke.thickness(), stroke.opacity(),
                    stroke.strokeOrder())).getId();
        }
        return privateStrokes.saveAndFlush(PrivateStroke.create(privateLayers.getReferenceById(layerId),
                stroke.tool(), points, null, stroke.color(), stroke.thickness(), stroke.opacity(),
                stroke.strokeOrder())).getId();
    }

    private void requireAccess(UUID slideId, User user, boolean shared, boolean edit) {
        Slide slide = slideRepository.findById(slideId)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
        SpaceMember member = memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                slide.getDocument().getSpace().getId(), user.getId(), SpaceMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(NoteErrorCode.NOTE_ACCESS_DENIED));
        if (!shared && member.getRole() != SpaceMemberRole.STUDENT) {
            throw new BusinessException(NoteErrorCode.NOTE_ACCESS_DENIED);
        }
        if (shared && edit && member.getRole() != SpaceMemberRole.PROFESSOR
                && (member.getRole() != SpaceMemberRole.ASSISTANT
                || !permissionRepository.existsBySpaceMemberIdAndPermission(
                        member.getId(), PermissionType.LECTURE_MATERIAL_MANAGE))) {
            throw new BusinessException(NoteErrorCode.NOTE_ACCESS_DENIED);
        }
    }

    private void validate(StrokeSyncRequest request) {
        if (request == null || !validator.validate(request).isEmpty()) invalid();
        Set<UUID> operationIds = new HashSet<>();
        Set<UUID> clientStrokeIds = new HashSet<>();
        int pointCount = 0;
        for (Operation operation : request.operations()) {
            if (!operationIds.add(operation.clientOperationId())) invalid();
            if (operation.type() == Type.CREATE) {
                if (operation.stroke() == null || operation.strokeId() != null) invalid();
                var stroke = operation.stroke();
                if (stroke.tool() != StrokeTool.PEN && stroke.tool() != StrokeTool.HIGHLIGHTER) invalid();
                if (!Double.isFinite(stroke.thickness()) || !Double.isFinite(stroke.opacity())) invalid();
                for (var point : stroke.points()) {
                    if (!Double.isFinite(point.xRatio()) || !Double.isFinite(point.yRatio())) invalid();
                }
                if (!clientStrokeIds.add(stroke.clientStrokeId())) {
                    throw new BusinessException(NoteErrorCode.NOTE_CLIENT_STROKE_ID_CONFLICT);
                }
                pointCount += stroke.points().size();
                if (pointCount > 50000) invalid();
            } else if (operation.strokeId() == null || operation.stroke() != null) {
                invalid();
            }
        }
    }

    private void invalid() { throw new BusinessException(CommonErrorCode.INVALID_INPUT); }

    @Transactional
    public FixerResponse.Created createFixer(UUID slideId, FixerRequest request, User user) {
        Slide slide = fixerSlide(slideId);
        requireProfessor(slide, user);
        if (request == null || !validator.validate(request).isEmpty()
                || !Double.isFinite(request.xRatio()) || !Double.isFinite(request.yRatio())) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        Fixer fixer = fixers.save(Fixer.create(slide, user, request.xRatio(), request.yRatio(), request.content().strip()));
        return FixerResponse.Created.of(fixer);
    }

    @Transactional(readOnly = true)
    public List<FixerResponse> getFixers(UUID slideId, User user) {
        requireProfessor(fixerSlide(slideId), user);
        return fixers.findAllBySlideIdAndProfessorIdOrderByCreatedAtAsc(slideId, user.getId())
                .stream().map(FixerResponse::of).toList();
    }

    @Transactional
    public FixerResponse.Checked checkFixer(UUID fixerId, User user) {
        Fixer fixer = fixers.findOwnedForUpdate(fixerId, user.getId())
                .orElseThrow(() -> new BusinessException(NoteErrorCode.FIXER_NOT_FOUND));
        requireProfessor(fixer.getSlide(), user);
        fixer.check();
        return new FixerResponse.Checked(fixer.getId(), fixer.isChecked());
    }

    private Slide fixerSlide(UUID id) {
        return slideRepository.findById(id)
                .orElseThrow(() -> new BusinessException(DocumentErrorCode.DOCUMENT_NOT_FOUND));
    }

    private void requireProfessor(Slide slide, User user) {
        SpaceMember member = memberRepository.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                slide.getDocument().getSpace().getId(), user.getId(), SpaceMemberStatus.APPROVED)
                .orElseThrow(() -> new BusinessException(NoteErrorCode.FIXER_ACCESS_DENIED));
        if (member.getRole() != SpaceMemberRole.PROFESSOR) {
            throw new BusinessException(NoteErrorCode.FIXER_ACCESS_DENIED);
        }
    }
}
