package com.tikitaka.note.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import com.tikitaka.document.entity.*;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.global.exception.*;
import com.tikitaka.note.dto.request.StrokeSyncRequest;
import com.tikitaka.note.dto.request.StrokeSyncRequest.*;
import com.tikitaka.note.entity.*;
import com.tikitaka.note.exception.NoteErrorCode;
import com.tikitaka.note.repository.*;
import com.tikitaka.space.entity.*;
import com.tikitaka.space.repository.*;
import com.tikitaka.user.entity.User;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class NoteServiceTests {
    static ValidatorFactory factory;
    @BeforeAll static void validation() { factory = Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidation() { factory.close(); }
    final SlideRepository slides = mock(SlideRepository.class);
    final SpaceMemberRepository members = mock(SpaceMemberRepository.class);
    final SpaceMemberPermissionRepository permissions = mock(SpaceMemberPermissionRepository.class);
    final PrivateLayerRepository privateLayers = mock(PrivateLayerRepository.class);
    final SharedLayerRepository sharedLayers = mock(SharedLayerRepository.class);
    final PrivateStrokeRepository privateStrokes = mock(PrivateStrokeRepository.class);
    final SharedStrokeRepository sharedStrokes = mock(SharedStrokeRepository.class);
    final PrivateStrokeOperationRepository privateOps = mock(PrivateStrokeOperationRepository.class);
    final SharedStrokeOperationRepository sharedOps = mock(SharedStrokeOperationRepository.class);
    final User user = mock(User.class);
    final UUID slideId = UUID.randomUUID(), layerId = UUID.randomUUID(), spaceId = UUID.randomUUID();
    final SpaceMember member = mock(SpaceMember.class);
    PrivateLayer layer;
    NoteService service;
    @BeforeEach void setup() {
        service = new NoteService(slides, members, permissions, privateLayers, sharedLayers,
                privateStrokes, sharedStrokes, privateOps, sharedOps, factory.getValidator(), mock(FixerRepository.class));
        Slide slide = mock(Slide.class);
        Document document = mock(Document.class);
        Space space = mock(Space.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(slides.findById(slideId)).thenReturn(Optional.of(slide));
        when(slide.getDocument()).thenReturn(document);
        when(document.getSpace()).thenReturn(space);
        when(space.getId()).thenReturn(spaceId);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(spaceId, user.getId(), SpaceMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));
        when(member.getRole()).thenReturn(SpaceMemberRole.STUDENT);
        layer = PrivateLayer.create(slide, user);
        org.springframework.test.util.ReflectionTestUtils.setField(layer, "id", layerId);
        when(privateLayers.findForUpdate(slideId, user.getId())).thenReturn(Optional.of(layer));
        when(privateLayers.getReferenceById(layerId)).thenReturn(layer);
    }
    Operation create() {
        return new Operation(UUID.randomUUID(), Type.CREATE, new Stroke(UUID.randomUUID(), StrokeTool.PEN,
                List.of(new Point(0.1,0.2)), "#000000", 2.0, 1.0, 0), null);
    }
    StrokeSyncRequest request(int version, Operation... operations) {
        return new StrokeSyncRequest(version, List.of(operations));
    }
    void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
    @Test void absentLayerReturnsEmptyVersionZeroWithoutCreating() {
        when(privateLayers.findForRead(slideId, user.getId())).thenReturn(Optional.empty());
        var result = service.getPrivate(slideId, user);
        assertThat(result.version()).isZero();
        assertThat(result.strokes()).isEmpty();
        verify(privateLayers, never()).ensureExists(any(), any());
    }
    @Test void createReturnsMappingAndIncreasesVersionOnce() {
        UUID strokeId = UUID.randomUUID();
        when(privateStrokes.saveAndFlush(any())).thenAnswer(invocation -> {
            PrivateStroke stroke = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(stroke, "id", strokeId);
            return stroke;
        });
        Operation operation = create();
        var result = service.syncPrivate(slideId, request(0, operation), user);
        assertThat(result.version()).isEqualTo(1);
        assertThat(layer.getVersion()).isEqualTo(1);
        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.createdStrokes().get(0).strokeId()).isEqualTo(strokeId);
        ArgumentCaptor<PrivateStrokeOperation> record = ArgumentCaptor.forClass(PrivateStrokeOperation.class);
        verify(privateOps).saveAndFlush(record.capture());
        assertThat(record.getValue().getRequestPayload()).isEqualTo(operation.payload());
        assertThat(record.getValue().getAppliedVersion()).isEqualTo(1);
    }
    @Test void staleIdenticalRetryReturnsOriginalMappingWithoutVersionIncrease() {
        Operation operation = create();
        UUID strokeId = UUID.randomUUID();
        layer.increaseVersion();
        when(privateOps.findAllByLayerIdAndClientOperationIdIn(eq(layerId), anyList()))
                .thenReturn(List.of(new PrivateStrokeOperation(layerId, operation.clientOperationId(), operation.payload(), strokeId, 1)));
        var result = service.syncPrivate(slideId, request(0, operation), user);
        assertThat(result.appliedCount()).isZero();
        assertThat(result.version()).isEqualTo(1);
        assertThat(result.createdStrokes().get(0).strokeId()).isEqualTo(strokeId);
        verifyNoInteractions(privateStrokes);
        verify(privateOps, never()).saveAndFlush(any());
    }
    @Test void changedOperationIdContentConflictsBeforeVersionCheck() {
        Operation operation = create();
        when(privateOps.findAllByLayerIdAndClientOperationIdIn(eq(layerId), anyList()))
                .thenReturn(List.of(new PrivateStrokeOperation(layerId, operation.clientOperationId(),
                        new Payload(Type.DELETE, null, UUID.randomUUID()), UUID.randomUUID(), 1)));
        error(() -> service.syncPrivate(slideId, request(9, operation), user), NoteErrorCode.NOTE_OPERATION_ID_CONFLICT);
        verifyNoInteractions(privateStrokes);
    }
    @Test void staleNewOperationConflictsWithoutApplying() {
        error(() -> service.syncPrivate(slideId, request(5, create()), user), NoteErrorCode.NOTE_VERSION_CONFLICT);
        assertThat(layer.getVersion()).isZero();
        verifyNoInteractions(privateStrokes);
    }
    @Test void mixedRetryAndNewDeleteOnlyCountsNewOperation() {
        Operation prior = create();
        UUID strokeId = UUID.randomUUID();
        layer.increaseVersion();
        when(privateOps.findAllByLayerIdAndClientOperationIdIn(eq(layerId), anyList())).thenReturn(List.of(
                new PrivateStrokeOperation(layerId, prior.clientOperationId(), prior.payload(), strokeId, 1)));
        PrivateStroke stroke = PrivateStroke.create(layer, StrokeTool.PEN, List.of(Map.of("x_ratio", 0.1, "y_ratio",0.2)), null, "#000000",2.0,1.0,0);
        when(privateStrokes.findByIdAndLayerId(strokeId, layerId)).thenReturn(Optional.of(stroke));
        var result = service.syncPrivate(slideId, request(1, prior, new Operation(UUID.randomUUID(), Type.DELETE, null, strokeId)), user);
        assertThat(result.appliedCount()).isEqualTo(1);
        assertThat(result.version()).isEqualTo(2);
        assertThat(stroke.isDeleted()).isTrue();
        assertThat(result.createdStrokes()).hasSize(1);
    }
    @Test void deleteOutsideLayerIsNotFoundAndLeavesVersionUnchanged() {
        UUID strokeId = UUID.randomUUID();
        when(privateStrokes.findByIdAndLayerId(strokeId, layerId)).thenReturn(Optional.empty());
        error(() -> service.syncPrivate(slideId, request(0, new Operation(UUID.randomUUID(), Type.DELETE, null, strokeId)), user),
                NoteErrorCode.NOTE_STROKE_NOT_FOUND);
        assertThat(layer.getVersion()).isZero();
        verify(privateOps, never()).saveAndFlush(any());
    }
    @Test void deleteOfAlreadyDeletedStrokeIsNoOp() {
        UUID strokeId = UUID.randomUUID();
        PrivateStroke stroke = PrivateStroke.create(layer, StrokeTool.PEN,
                List.of(Map.of("x_ratio", 0.1, "y_ratio", 0.2)), null, "#000000", 2.0, 1.0, 0);
        stroke.delete();
        when(privateStrokes.findByIdAndLayerId(strokeId, layerId)).thenReturn(Optional.of(stroke));

        var result = service.syncPrivate(slideId,
                request(0, new Operation(UUID.randomUUID(), Type.DELETE, null, strokeId)), user);

        assertThat(result.appliedCount()).isZero();
        assertThat(result.version()).isZero();
        verify(privateOps, never()).saveAndFlush(any());
    }
    @Test void reusedClientStrokeIdConflicts() {
        when(privateOps.existsByLayerIdAndClientStrokeId(eq(layerId), any())).thenReturn(true);
        error(() -> service.syncPrivate(slideId, request(0, create()), user), NoteErrorCode.NOTE_CLIENT_STROKE_ID_CONFLICT);
        verifyNoInteractions(privateStrokes);
    }
    @Test void emptyBatchAndDuplicateOperationIdsAreRejected() {
        error(() -> service.syncPrivate(slideId, new StrokeSyncRequest(0,List.of()), user), CommonErrorCode.INVALID_INPUT);
        Operation operation = create();
        error(() -> service.syncPrivate(slideId, request(0, operation, operation), user), CommonErrorCode.INVALID_INPUT);
        verify(privateLayers, never()).ensureExists(any(), any());
    }
    @Test void textAndNonFiniteThicknessAreRejected() {
        Operation operation = create();
        var s = operation.stroke();
        var text = new Stroke(s.clientStrokeId(), StrokeTool.TEXT, s.points(),s.color(),s.thickness(),s.opacity(),0);
        error(() -> service.syncPrivate(slideId, request(0,new Operation(operation.clientOperationId(),Type.CREATE,text,null)), user), CommonErrorCode.INVALID_INPUT);
        var infinite = new Stroke(s.clientStrokeId(), StrokeTool.PEN,s.points(),s.color(),Double.POSITIVE_INFINITY,1.0,0);
        error(() -> service.syncPrivate(slideId, request(0,new Operation(operation.clientOperationId(),Type.CREATE,infinite,null)), user), CommonErrorCode.INVALID_INPUT);
    }
    @Test void wrongShapeIsRejected() {
        error(() -> service.syncPrivate(slideId,request(0,new Operation(UUID.randomUUID(),Type.CREATE,null,null)),user),CommonErrorCode.INVALID_INPUT);
        error(() -> service.syncPrivate(slideId,request(0,new Operation(UUID.randomUUID(),Type.DELETE,create().stroke(),UUID.randomUUID())),user),CommonErrorCode.INVALID_INPUT);
    }
    @Test void professorCannotUseStudentPrivateApi() {
        when(member.getRole()).thenReturn(SpaceMemberRole.PROFESSOR);
        error(() -> service.getPrivate(slideId,user),NoteErrorCode.NOTE_ACCESS_DENIED);
        error(() -> service.syncPrivate(slideId,request(0,create()),user),NoteErrorCode.NOTE_ACCESS_DENIED);
        verifyNoInteractions(privateLayers);
    }
    @Test void studentCanReadSharedButCannotEdit() {
        when(sharedLayers.findForRead(slideId)).thenReturn(Optional.empty());
        assertThat(service.getShared(slideId,user).strokes()).isEmpty();
        error(() -> service.syncShared(slideId,request(0,create()),user),NoteErrorCode.NOTE_ACCESS_DENIED);
        verify(sharedLayers,never()).ensureExists(any());
    }
    @Test void assistantRequiresMaterialPermission() {
        when(member.getRole()).thenReturn(SpaceMemberRole.ASSISTANT);
        error(() -> service.syncShared(slideId,request(0,create()),user),NoteErrorCode.NOTE_ACCESS_DENIED);
        when(permissions.existsBySpaceMemberIdAndPermission(member.getId(),PermissionType.LECTURE_MATERIAL_MANAGE)).thenReturn(true);
        SharedLayer shared = SharedLayer.create(mock(Slide.class));
        org.springframework.test.util.ReflectionTestUtils.setField(shared,"id",layerId);
        when(sharedLayers.findForUpdate(slideId)).thenReturn(Optional.of(shared));
        UUID strokeId = UUID.randomUUID();
        when(sharedStrokes.findByIdAndLayerId(strokeId,layerId)).thenReturn(Optional.of(mock(SharedStroke.class)));
        assertThat(service.syncShared(slideId,request(0,new Operation(UUID.randomUUID(),Type.DELETE,null,strokeId)),user).version()).isEqualTo(1);
        verifyNoInteractions(privateLayers,privateStrokes,privateOps);
    }
    @Test void removedMemberCannotRetryPreviouslyAppliedOperation() {
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(spaceId,user.getId(),SpaceMemberStatus.APPROVED)).thenReturn(Optional.empty());
        error(() -> service.syncPrivate(slideId,request(0,create()),user),NoteErrorCode.NOTE_ACCESS_DENIED);
        verifyNoInteractions(privateOps);
    }
}
