package com.tikitaka.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.question.dto.request.CategoryCreateRequest;
import com.tikitaka.question.entity.CategorySourceType;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.repository.AnswerRepository;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionCommentRepository;
import com.tikitaka.question.repository.QuestionLikeRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.search.repository.RecentQuestionViewRepository;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

class QuestionCategoryManagementTests {

    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final QuestionCategoryRepository categories = mock(QuestionCategoryRepository.class);
    private final QuestionCategoryMappingRepository mappings = mock(QuestionCategoryMappingRepository.class);
    private final SpaceMemberRepository members = mock(SpaceMemberRepository.class);
    private final CategoryQuestionRemappingService remapping = mock(CategoryQuestionRemappingService.class);

    private final QuestionService service = new QuestionService(
            questions,
            mock(AnswerRepository.class),
            mock(QuestionCommentRepository.class),
            mock(QuestionLikeRepository.class),
            mock(RecentQuestionViewRepository.class),
            categories,
            mappings,
            mock(DocumentRepository.class),
            mock(SlideRepository.class),
            mock(DocumentStorage.class),
            members,
            mock(SpaceMemberPermissionRepository.class),
            mock(CursorCodec.class),
            mock(QuestionAiProcessingService.class),
            mock(SimilarQuestionService.class),
            remapping,
            mock(TransactionTemplate.class),
            mock(com.tikitaka.notification.service.NotificationService.class)
    );

    @Test
    void qst025CreatesDocumentCategoryMapsTargetQuestionAndRemapsOnlyOthers() {
        TestContext context = managerContext();
        QuestionCategory category = mock(QuestionCategory.class);
        UUID categoryId = UUID.randomUUID();

        when(categories.existsByDocumentIdAndNameAndDeletedFalse(
                context.documentId(), "중간고사"))
                .thenReturn(false);
        when(categories.save(any(QuestionCategory.class))).thenReturn(category);
        when(category.getId()).thenReturn(categoryId);
        when(category.getName()).thenReturn("중간고사");
        when(category.getSourceType()).thenReturn(CategorySourceType.MANUAL);

        var result = service.createQuestionCategory(
                context.questionId(), new CategoryCreateRequest("중간고사"), context.user());

        assertThat(result.questionId()).isEqualTo(context.questionId());
        assertThat(result.categoryId()).isEqualTo(categoryId);
        assertThat(result.documentId()).isEqualTo(context.documentId());
        assertThat(result.name()).isEqualTo("중간고사");
        verify(categories).save(any(QuestionCategory.class));
        verify(mappings).save(any(QuestionCategoryMapping.class));
        verify(remapping).scheduleRecalculation(context.documentId(), context.questionId());
    }

    @Test
    void qst026DeletesOnlyTheRequestedQuestionCategoryMappingWithoutRemapping() {
        TestContext context = managerContext();
        UUID categoryId = UUID.randomUUID();
        QuestionCategory category = category(context.document(), categoryId, "중간고사");
        QuestionCategory anotherCategory = mock(QuestionCategory.class);
        QuestionCategoryMapping targetMapping = mock(QuestionCategoryMapping.class);
        QuestionCategoryMapping otherMapping = mock(QuestionCategoryMapping.class);

        when(categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(targetMapping.getCategory()).thenReturn(category);
        when(otherMapping.getCategory()).thenReturn(anotherCategory);
        when(anotherCategory.getId()).thenReturn(UUID.randomUUID());
        when(mappings.findAllByQuestionId(context.questionId()))
                .thenReturn(List.of(targetMapping, otherMapping));

        var result = service.deleteQuestionCategory(
                context.questionId(), categoryId, context.user());

        assertThat(result.questionId()).isEqualTo(context.questionId());
        assertThat(result.categoryId()).isEqualTo(categoryId);
        verify(mappings).delete(targetMapping);
        verify(mappings, never()).delete(otherMapping);
        verifyNoInteractions(remapping);
    }

    private TestContext managerContext() {
        UUID questionId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Question question = mock(Question.class);
        Document document = mock(Document.class);
        Space space = mock(Space.class);
        User user = mock(User.class);
        SpaceMember member = mock(SpaceMember.class);

        when(questions.findById(questionId)).thenReturn(Optional.of(question));
        when(question.getId()).thenReturn(questionId);
        when(question.isDeleted()).thenReturn(false);
        when(question.getDocument()).thenReturn(document);
        when(document.getId()).thenReturn(documentId);
        when(document.getSpace()).thenReturn(space);
        when(space.getId()).thenReturn(spaceId);
        when(user.getId()).thenReturn(userId);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                spaceId, userId, SpaceMemberStatus.APPROVED))
                .thenReturn(Optional.of(member));
        when(member.getRole()).thenReturn(SpaceMemberRole.PROFESSOR);

        return new TestContext(questionId, documentId, question, document, user);
    }

    private QuestionCategory category(
            Document document,
            UUID categoryId,
            String name
    ) {
        QuestionCategory category = mock(QuestionCategory.class);
        when(category.getId()).thenReturn(categoryId);
        when(category.getDocument()).thenReturn(document);
        when(category.getName()).thenReturn(name);
        when(category.getSourceType()).thenReturn(CategorySourceType.MANUAL);
        when(category.isDeleted()).thenReturn(false);
        return category;
    }

    private record TestContext(
            UUID questionId,
            UUID documentId,
            Question question,
            Document document,
            User user
    ) {
    }
}
