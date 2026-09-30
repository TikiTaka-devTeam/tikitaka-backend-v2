package com.tikitaka.question.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.document.storage.DocumentStorage;
import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.question.dto.request.CommentCreateRequest;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionComment;
import com.tikitaka.question.exception.QuestionErrorCode;
import com.tikitaka.question.repository.AnswerRepository;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionCommentRepository;
import com.tikitaka.question.repository.QuestionLikeRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.search.repository.RecentQuestionViewRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.Space;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

class QuestionCommentServiceTests {

    private final QuestionRepository questions = mock(QuestionRepository.class);
    private final AnswerRepository answers = mock(AnswerRepository.class);
    private final QuestionCommentRepository comments = mock(QuestionCommentRepository.class);
    private final QuestionLikeRepository likes = mock(QuestionLikeRepository.class);
    private final RecentQuestionViewRepository recentViews = mock(RecentQuestionViewRepository.class);
    private final QuestionCategoryRepository categories = mock(QuestionCategoryRepository.class);
    private final QuestionCategoryMappingRepository mappings = mock(QuestionCategoryMappingRepository.class);
    private final DocumentRepository documents = mock(DocumentRepository.class);
    private final SlideRepository slides = mock(SlideRepository.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final SpaceMemberRepository members = mock(SpaceMemberRepository.class);
    private final SpaceMemberPermissionRepository permissions = mock(SpaceMemberPermissionRepository.class);

    private final QuestionService service = new QuestionService(
            questions,
            answers,
            comments,
            likes,
            recentViews,
            categories,
            mappings,
            documents,
            slides,
            storage,
            members,
            permissions,
            mock(CursorCodec.class),
            mock(QuestionAiProcessingService.class),
            mock(SimilarQuestionService.class),
            mock(CategoryQuestionRemappingService.class),
            mock(TransactionTemplate.class)
    );

    private final UUID spaceId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final UUID authorId = UUID.randomUUID();
    private final Space space = mock(Space.class);
    private final Document document = mock(Document.class);
    private final Question question = mock(Question.class);
    private final User questionAuthor = mock(User.class);

    @BeforeEach
    void setUp() {
        when(space.getId()).thenReturn(spaceId);
        when(document.getSpace()).thenReturn(space);
        when(question.getId()).thenReturn(questionId);
        when(question.getDocument()).thenReturn(document);
        when(question.getStudent()).thenReturn(questionAuthor);
        when(questionAuthor.getId()).thenReturn(authorId);
        when(questions.findById(questionId)).thenReturn(Optional.of(question));
        when(comments.save(any(QuestionComment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void questionAuthorCanAddAnonymousCommentToOwnQuestion() {
        approve(questionAuthor, SpaceMemberRole.STUDENT, false);

        var response = service.addComment(
                questionId,
                new CommentCreateRequest("추가 질문입니다.", null),
                questionAuthor
        );

        assertThat(response.isAnonymous()).isTrue();
        verify(comments).save(any(QuestionComment.class));
        verify(question, never()).markAnswered();
        verify(question, never()).markPending();
    }

    @Test
    void otherStudentCannotAddComment() {
        User otherStudent = user(UUID.randomUUID());
        approve(otherStudent, SpaceMemberRole.STUDENT, false);

        assertThatThrownBy(() -> service.addComment(
                questionId,
                new CommentCreateRequest("다른 학생 댓글", null),
                otherStudent
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(QuestionErrorCode.COMMENT_CREATE_FORBIDDEN);

        verify(comments, never()).save(any());
    }

    @Test
    void professorCanAddNonAnonymousComment() {
        User professor = user(UUID.randomUUID());
        approve(professor, SpaceMemberRole.PROFESSOR, false);

        var response = service.addComment(
                questionId,
                new CommentCreateRequest("교수 설명입니다.", null),
                professor
        );

        assertThat(response.isAnonymous()).isFalse();
    }

    @Test
    void assistantRequiresQuestionManagePermission() {
        User allowedAssistant = user(UUID.randomUUID());
        SpaceMember allowedMember = approve(
                allowedAssistant,
                SpaceMemberRole.ASSISTANT,
                true
        );

        assertThat(service.addComment(
                questionId,
                new CommentCreateRequest("조교 설명입니다.", null),
                allowedAssistant
        ).isAnonymous()).isFalse();

        verify(permissions).existsBySpaceMemberIdAndPermission(
                allowedMember.getId(),
                PermissionType.QUESTION_MANAGE
        );
    }

    @Test
    void assistantWithoutQuestionManagePermissionCannotAddComment() {
        User assistant = user(UUID.randomUUID());
        approve(assistant, SpaceMemberRole.ASSISTANT, false);

        assertThatThrownBy(() -> service.addComment(
                questionId,
                new CommentCreateRequest("권한 없는 조교 댓글", null),
                assistant
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(QuestionErrorCode.COMMENT_CREATE_FORBIDDEN);
    }

    @Test
    void questionAuthorStillCannotCreateOfficialAnswer() {
        approve(questionAuthor, SpaceMemberRole.STUDENT, false);

        assertThatThrownBy(() -> service.addAnswer(
                questionId,
                "공식 답변 시도",
                questionAuthor
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(QuestionErrorCode.QUESTION_MANAGE_FORBIDDEN);
    }

    @Test
    void detailAnonymizesQuestionAuthor() {
        User viewer = user(UUID.randomUUID());
        approve(viewer, SpaceMemberRole.STUDENT, false);

        QuestionComment comment = mock(QuestionComment.class);
        when(comment.getQuestion()).thenReturn(question);
        when(comment.getAuthor()).thenReturn(questionAuthor);
        when(comment.getContent()).thenReturn("익명 후속 질문");
        when(comments.findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(questionId))
                .thenReturn(List.of(comment));

        var response = service.detail(questionId, viewer);
        var anonymousComment = response.comments().get(0);

        assertThat(anonymousComment.isAnonymous()).isTrue();
        assertThat(anonymousComment.author().userId()).isNull();
        assertThat(anonymousComment.author().name()).isEqualTo("질문자");
        assertThat(anonymousComment.author().profileUrl()).isNull();
    }

    @Test
    void detailReturnsSignedSlideThumbnailUrl() {
        User viewer = user(UUID.randomUUID());
        approve(viewer, SpaceMemberRole.STUDENT, false);

        Slide slide = mock(Slide.class);
        UUID slideId = UUID.randomUUID();
        when(slide.getId()).thenReturn(slideId);
        when(slide.getPageNumber()).thenReturn(3);
        when(slide.getThumbnailKey()).thenReturn("slides/page-3.png");
        when(storage.presignedGetUrl("slides/page-3.png"))
                .thenReturn("https://example.com/signed-slide.png");
        when(question.getSlide()).thenReturn(slide);

        var response = service.detail(questionId, viewer);

        assertThat(response.slide().slideId()).isEqualTo(slideId);
        assertThat(response.slide().pageNumber()).isEqualTo(3);
        assertThat(response.slide().thumbnailUrl())
                .isEqualTo("https://example.com/signed-slide.png");
    }

    private SpaceMember approve(
            User user,
            SpaceMemberRole role,
            boolean hasQuestionManagePermission
    ) {
        SpaceMember member = mock(SpaceMember.class);
        UUID memberId = UUID.randomUUID();
        when(member.getId()).thenReturn(memberId);
        when(member.getRole()).thenReturn(role);
        when(members.findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                spaceId,
                user.getId(),
                SpaceMemberStatus.APPROVED
        )).thenReturn(Optional.of(member));
        when(permissions.existsBySpaceMemberIdAndPermission(
                memberId,
                PermissionType.QUESTION_MANAGE
        )).thenReturn(hasQuestionManagePermission);
        return member;
    }

    private User user(UUID id) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        return user;
    }
}
