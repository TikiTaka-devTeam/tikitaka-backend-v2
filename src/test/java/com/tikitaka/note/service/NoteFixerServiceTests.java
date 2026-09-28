package com.tikitaka.note.service;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import com.tikitaka.document.entity.*;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.note.dto.request.FixerRequest;
import com.tikitaka.note.entity.Fixer;
import com.tikitaka.note.exception.NoteErrorCode;
import com.tikitaka.note.repository.*;
import com.tikitaka.global.exception.*;
import com.tikitaka.space.entity.*;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;
import jakarta.validation.*;
import org.junit.jupiter.api.*;
class NoteFixerServiceTests {
    static ValidatorFactory factory;
    @BeforeAll static void validation() { factory=Validation.buildDefaultValidatorFactory(); }
    @AfterAll static void closeValidation() { factory.close(); }
    final FixerRepository fixers=mock(FixerRepository.class);
    final SlideRepository slides=mock(SlideRepository.class);
    final SpaceMemberRepository members=mock(SpaceMemberRepository.class);
    final User user=mock(User.class);
    final Slide slide=mock(Slide.class);
    final SpaceMember member=mock(SpaceMember.class);
    final UUID slideId=UUID.randomUUID(), fixerId=UUID.randomUUID(), spaceId=UUID.randomUUID();
    NoteService service;
    @BeforeEach void setup() {
        service=new NoteService(slides,members,mock(com.tikitaka.space.repository.SpaceMemberPermissionRepository.class), mock(PrivateLayerRepository.class),mock(SharedLayerRepository.class),mock(PrivateStrokeRepository.class),mock(SharedStrokeRepository.class),mock(PrivateStrokeOperationRepository.class),mock(SharedStrokeOperationRepository.class),factory.getValidator(),fixers,mock(org.springframework.context.ApplicationEventPublisher.class));
        Document document=mock(Document.class); Space space=mock(Space.class);
        when(user.getId()).thenReturn(UUID.randomUUID());
        when(slide.getId()).thenReturn(slideId);
        when(slides.findById(slideId)).thenReturn(Optional.of(slide));
        when(slide.getDocument()).thenReturn(document); when(document.getSpace()).thenReturn(space);
        when(space.getId()).thenReturn(spaceId);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(spaceId,user.getId(),SpaceMemberStatus.APPROVED)).thenReturn(Optional.of(member));
        when(member.getRole()).thenReturn(SpaceMemberRole.PROFESSOR);
    }
    void error(Runnable action,ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,e->assertThat(e.getErrorCode()).isEqualTo(code));
    }
    @Test void createStripsContentAndUsesAuthenticatedProfessor() {
        when(fixers.save(any())).thenAnswer(i->{ Fixer fixer=i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(fixer,"id",fixerId); return fixer; });
        var result=service.createFixer(slideId,new FixerRequest(0.1,0.2,"  수정 필요  "),user);
        assertThat(result.content()).isEqualTo("수정 필요");
        assertThat(result.slideId()).isEqualTo(slideId); assertThat(result.checked()).isFalse();
    }
    @Test void repeatedCheckPreservesTimestamp() {
        Fixer fixer=Fixer.create(slide,user,0.1,0.2,"수정");
        org.springframework.test.util.ReflectionTestUtils.setField(fixer,"id",fixerId);
        when(fixers.findOwnedForUpdate(fixerId,user.getId())).thenReturn(Optional.of(fixer));
        assertThat(service.checkFixer(fixerId,user).checked()).isTrue();
        var first=fixer.getCheckedAt(); service.checkFixer(fixerId,user);
        assertThat(fixer.getCheckedAt()).isEqualTo(first);
    }
    @Test void anotherProfessorMemoReturnsNotFound() {
        when(fixers.findOwnedForUpdate(fixerId,user.getId())).thenReturn(Optional.empty());
        error(()->service.checkFixer(fixerId,user),NoteErrorCode.FIXER_NOT_FOUND);
    }
    @Test void studentAndAssistantCannotReadProfessorMemo() {
        for (var role:List.of(SpaceMemberRole.STUDENT,SpaceMemberRole.ASSISTANT)) {
            when(member.getRole()).thenReturn(role);
            error(()->service.getFixers(slideId,user),NoteErrorCode.FIXER_ACCESS_DENIED);
        }
        verifyNoInteractions(fixers);
    }
    @Test void invalidCoordinatesAndBlankContentAreRejected() {
        error(()->service.createFixer(slideId,new FixerRequest(1.1,0.2,"수정"),user),CommonErrorCode.INVALID_INPUT);
        error(()->service.createFixer(slideId,new FixerRequest(0.1,0.2,"  "),user),CommonErrorCode.INVALID_INPUT);
        error(()->service.createFixer(slideId,new FixerRequest(Double.NaN,0.2,"수정"),user),CommonErrorCode.INVALID_INPUT);
        verify(fixers,never()).save(any());
    }
}
