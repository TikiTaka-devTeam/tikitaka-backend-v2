package com.tikitaka.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionStatus;
import com.tikitaka.question.exception.QuestionErrorCode;
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

class QuestionListCategoryFilterTests {

    @Test
    void filtersByCategoryBeforeSortingAndPagination() {
        Fixture fixture = new Fixture(SpaceMemberRole.PROFESSOR);
        UUID categoryId = UUID.randomUUID();
        QuestionCategory category = fixture.category(categoryId, fixture.document);
        Question older = fixture.question(fixture.document, Instant.parse("2026-01-01T00:00:00Z"));
        Question newer = fixture.question(fixture.document, Instant.parse("2026-01-02T00:00:00Z"));
        Question newest = fixture.question(fixture.document, Instant.parse("2026-01-03T00:00:00Z"));
        Question unrelated = fixture.question(fixture.document, Instant.parse("2026-01-04T00:00:00Z"));

        when(fixture.categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(fixture.questions.findAllByDocumentSpaceIdAndDeletedFalse(fixture.spaceId))
                .thenReturn(List.of(older, newer, newest, unrelated));
        fixture.map(older, category);
        fixture.map(newer, category);
        fixture.map(newest, category);
        fixture.map(unrelated, fixture.category(UUID.randomUUID(), fixture.document));
        when(fixture.cursorCodec.encode(any())).thenReturn("next-cursor");

        var result = fixture.service.list(
                fixture.spaceId, QuestionService.QuestionSortType.LATEST,
                null, categoryId, null, 2, fixture.user, false);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.questions()).extracting(item -> item.questionId())
                .containsExactly(newest.getId(), newer.getId());
        assertThat(result.nextCursor()).isEqualTo("next-cursor");
        assertThat(result.hasNext()).isTrue();
    }

    @Test
    void filtersOnlyMyQuestionsWhenMineIsRequested() {
        Fixture fixture = new Fixture(SpaceMemberRole.STUDENT);
        UUID categoryId = UUID.randomUUID();
        QuestionCategory category = fixture.category(categoryId, fixture.document);
        Question mine = fixture.question(fixture.document, Instant.now());

        when(fixture.categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(fixture.questions.findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(
                fixture.spaceId, fixture.userId)).thenReturn(List.of(mine));
        fixture.map(mine, category);

        var result = fixture.service.list(
                fixture.spaceId, QuestionService.QuestionSortType.LATEST,
                null, categoryId, null, 20, fixture.user, true);

        assertThat(result.questions()).extracting(item -> item.questionId())
                .containsExactly(mine.getId());
        verify(fixture.questions).findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(
                fixture.spaceId, fixture.userId);
        verify(fixture.questions, never()).findAllByDocumentSpaceIdAndDeletedFalse(fixture.spaceId);
    }

    @Test
    void appliesDocumentAndCategoryFiltersTogether() {
        Fixture fixture = new Fixture(SpaceMemberRole.PROFESSOR);
        UUID categoryId = UUID.randomUUID();
        Document anotherDocument = fixture.document(fixture.space, UUID.randomUUID());
        QuestionCategory category = fixture.category(categoryId, fixture.document);
        Question inRequestedDocument = fixture.question(fixture.document, Instant.now());
        Question inAnotherDocument = fixture.question(anotherDocument, Instant.now().minusSeconds(1));

        when(fixture.documents.findById(fixture.documentId)).thenReturn(Optional.of(fixture.document));
        when(fixture.categories.findById(categoryId)).thenReturn(Optional.of(category));
        when(fixture.questions.findAllByDocumentSpaceIdAndDeletedFalse(fixture.spaceId))
                .thenReturn(List.of(inRequestedDocument, inAnotherDocument));
        fixture.map(inRequestedDocument, category);
        fixture.map(inAnotherDocument, category);

        var result = fixture.service.list(
                fixture.spaceId, QuestionService.QuestionSortType.LATEST,
                fixture.documentId, categoryId, null, 20, fixture.user, false);

        assertThat(result.questions()).extracting(item -> item.questionId())
                .containsExactly(inRequestedDocument.getId());
    }

    @Test
    void rejectsCategoryOutsideRequestedSpace() {
        Fixture fixture = new Fixture(SpaceMemberRole.PROFESSOR);
        UUID categoryId = UUID.randomUUID();
        Space anotherSpace = mock(Space.class);
        when(anotherSpace.getId()).thenReturn(UUID.randomUUID());
        QuestionCategory category = fixture.category(
                categoryId, fixture.document(anotherSpace, UUID.randomUUID()));
        when(fixture.categories.findById(categoryId)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> fixture.service.list(
                fixture.spaceId, QuestionService.QuestionSortType.LATEST,
                null, categoryId, null, 20, fixture.user, false))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(QuestionErrorCode.CATEGORY_NOT_FOUND);
        verify(fixture.questions, never()).findAllByDocumentSpaceIdAndDeletedFalse(any());
    }

    @Test
    void rejectsCategoryOutsideRequestedDocument() {
        Fixture fixture = new Fixture(SpaceMemberRole.PROFESSOR);
        UUID categoryId = UUID.randomUUID();
        Document anotherDocument = fixture.document(fixture.space, UUID.randomUUID());
        QuestionCategory category = fixture.category(categoryId, fixture.document);
        when(fixture.documents.findById(anotherDocument.getId()))
                .thenReturn(Optional.of(anotherDocument));
        when(fixture.categories.findById(categoryId)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> fixture.service.list(
                fixture.spaceId, QuestionService.QuestionSortType.LATEST,
                anotherDocument.getId(), categoryId, null, 20, fixture.user, false))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(QuestionErrorCode.CATEGORY_NOT_FOUND);
    }

    @Test
    void rejectsDeletedCategory() {
        Fixture fixture = new Fixture(SpaceMemberRole.PROFESSOR);
        UUID categoryId = UUID.randomUUID();
        QuestionCategory category = fixture.category(categoryId, fixture.document);
        when(category.isDeleted()).thenReturn(true);
        when(fixture.categories.findById(categoryId)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> fixture.service.list(
                fixture.spaceId, QuestionService.QuestionSortType.LATEST,
                null, categoryId, null, 20, fixture.user, false))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(QuestionErrorCode.CATEGORY_NOT_FOUND);
    }

    private static final class Fixture {
        private final QuestionRepository questions = mock(QuestionRepository.class);
        private final QuestionCategoryRepository categories = mock(QuestionCategoryRepository.class);
        private final QuestionCategoryMappingRepository mappings = mock(QuestionCategoryMappingRepository.class);
        private final DocumentRepository documents = mock(DocumentRepository.class);
        private final SpaceMemberRepository members = mock(SpaceMemberRepository.class);
        private final CursorCodec cursorCodec = mock(CursorCodec.class);
        private final UUID spaceId = UUID.randomUUID();
        private final UUID documentId = UUID.randomUUID();
        private final UUID userId = UUID.randomUUID();
        private final Space space = mock(Space.class);
        private final Document document = document(space, documentId);
        private final User user = mock(User.class);
        private final QuestionService service;

        private Fixture(SpaceMemberRole role) {
            SpaceMember member = mock(SpaceMember.class);
            when(user.getId()).thenReturn(userId);
            when(space.getId()).thenReturn(spaceId);
            when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                    spaceId, userId, SpaceMemberStatus.APPROVED))
                    .thenReturn(Optional.of(member));
            when(member.getRole()).thenReturn(role);

            service = new QuestionService(
                    questions, mock(AnswerRepository.class), mock(QuestionCommentRepository.class),
                    mock(QuestionLikeRepository.class), mock(RecentQuestionViewRepository.class),
                    categories, mappings, documents, mock(SlideRepository.class), members,
                    mock(SpaceMemberPermissionRepository.class), cursorCodec,
                    mock(QuestionAiProcessingService.class), mock(SimilarQuestionService.class),
                    mock(CategoryQuestionRemappingService.class), mock(TransactionTemplate.class));
        }

        private Document document(Space owner, UUID id) {
            Document value = mock(Document.class);
            when(value.getId()).thenReturn(id);
            when(value.getSpace()).thenReturn(owner);
            when(value.getTitle()).thenReturn("Document");
            return value;
        }

        private QuestionCategory category(UUID id, Document document) {
            QuestionCategory value = mock(QuestionCategory.class);
            when(value.getId()).thenReturn(id);
            when(value.getDocument()).thenReturn(document);
            when(value.getName()).thenReturn("Category");
            when(value.isDeleted()).thenReturn(false);
            return value;
        }

        private Question question(Document document, Instant createdAt) {
            Question value = mock(Question.class);
            when(value.getId()).thenReturn(UUID.randomUUID());
            when(value.getDocument()).thenReturn(document);
            when(value.getTitle()).thenReturn("Question");
            when(value.getSlide()).thenReturn(null);
            when(value.getCreatedAt()).thenReturn(createdAt);
            when(value.getViewCount()).thenReturn(0);
            when(value.getLikeCount()).thenReturn(0);
            when(value.getStatus()).thenReturn(QuestionStatus.PENDING);
            return value;
        }

        private void map(Question question, QuestionCategory category) {
            QuestionCategoryMapping mapping = mock(QuestionCategoryMapping.class);
            when(mapping.getCategory()).thenReturn(category);
            when(mappings.findAllByQuestionId(question.getId())).thenReturn(List.of(mapping));
        }
    }
}
