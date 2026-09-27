package com.tikitaka.question.service;

import static com.tikitaka.question.dto.response.QuestionResponse.*;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.entity.Slide;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.document.repository.SlideRepository;
import com.tikitaka.global.common.cursor.CursorCodec;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.question.dto.request.CategoryBatchRequest;
import com.tikitaka.question.dto.request.CategoryCreateRequest;
import com.tikitaka.question.dto.request.CategoryUpdateRequest;
import com.tikitaka.question.dto.request.CommentCreateRequest;
import com.tikitaka.question.dto.request.QuestionCreateRequest;
import com.tikitaka.question.dto.request.SpaceQuestionCreateRequest;
import com.tikitaka.question.entity.Answer;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionComment;
import com.tikitaka.question.entity.QuestionLike;
import com.tikitaka.question.entity.QuestionStatus;
import com.tikitaka.question.exception.QuestionErrorCode;
import com.tikitaka.question.repository.AnswerRepository;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionCommentRepository;
import com.tikitaka.question.repository.QuestionLikeRepository;
import com.tikitaka.question.repository.QuestionRepository;
import com.tikitaka.search.repository.RecentQuestionViewRepository;
import com.tikitaka.space.entity.PermissionType;
import com.tikitaka.space.entity.SpaceMember;
import com.tikitaka.space.entity.SpaceMemberRole;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberPermissionRepository;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final QuestionRepository questions;
    private final AnswerRepository answers;
    private final QuestionCommentRepository comments;
    private final QuestionLikeRepository likes;
    private final RecentQuestionViewRepository recentQuestionViews;
    private final QuestionCategoryRepository categories;
    private final QuestionCategoryMappingRepository mappings;
    private final DocumentRepository documents;
    private final SlideRepository slides;
    private final SpaceMemberRepository members;
    private final SpaceMemberPermissionRepository permissions;
    private final CursorCodec cursorCodec;

    private final QuestionAiProcessingService questionAiProcessingService;
    private final SimilarQuestionService similarQuestionService;
    private final CategoryQuestionRemappingService categoryQuestionRemappingService;
    private final TransactionTemplate transactionTemplate;

    public enum QuestionSortType {
        MOST_VIEWED,
        MOST_POPULAR,
        LATEST
    }

    public enum QuestionScope {
        ALL,
        SLIDE
    }

    private record OffsetCursor(int offset) {
    }

    public ListResponse list(
            UUID spaceId,
            QuestionSortType sort,
            UUID documentId,
            UUID categoryId,
            String cursor,
            int size,
            User user,
            boolean mine
    ) {
        SpaceMember member = requireMember(spaceId, user);

        if (mine && member.getRole() != SpaceMemberRole.STUDENT) {
            fail(QuestionErrorCode.STUDENT_ONLY);
        }

        if (documentId != null) {
            requireDocument(documentId, spaceId);
        }

        if (categoryId != null) {
            requireCategoryInScope(categoryId, spaceId, documentId);
        }

        int pageSize = size(size);
        int offset = offset(cursor);

        List<Question> filtered =
                (mine
                        ? questions.findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(
                                spaceId,
                                user.getId()
                        )
                        : questions.findAllByDocumentSpaceIdAndDeletedFalse(spaceId))
                        .stream()
                        .filter(q ->
                                documentId == null
                                        || q.getDocument()
                                        .getId()
                                        .equals(documentId)
                        )
                        .filter(q ->
                                categoryId == null
                                        || mappings.findAllByQuestionId(q.getId())
                                        .stream()
                                        .anyMatch(m ->
                                                m.getCategory()
                                                        .getId()
                                                        .equals(categoryId)
                                                        && !m.getCategory()
                                                        .isDeleted()
                                        )
                        )
                        .sorted(comparator(sort))
                        .toList();

        List<Question> page = filtered.stream()
                .skip(offset)
                .limit(pageSize)
                .toList();

        boolean hasNext =
                offset + page.size() < filtered.size();

        return new ListResponse(
                page.stream()
                        .map(question -> listItem(question, user))
                        .toList(),
                filtered.size(),
                hasNext
                        ? cursorCodec.encode(
                                new OffsetCursor(
                                        offset + page.size()
                                )
                        )
                        : null,
                hasNext
        );
    }

    public Summary summary(
            UUID spaceId,
            User user
    ) {
        SpaceMember member =
                requireMember(spaceId, user);

        if (member.getRole() != SpaceMemberRole.STUDENT) {
            fail(QuestionErrorCode.STUDENT_ONLY);
        }

        long total =
                questions
                        .countByDocumentSpaceIdAndStudentIdAndDeletedFalse(
                                spaceId,
                                user.getId()
                        );

        long answered =
                questions
                        .findAllByDocumentSpaceIdAndStudentIdAndDeletedFalse(
                                spaceId,
                                user.getId()
                        )
                        .stream()
                        .filter(q ->
                                q.getStatus()
                                        == QuestionStatus.ANSWERED
                        )
                        .count();

        return new Summary(
                total,
                answered,
                total - answered
        );
    }

    public DocumentListResponse documentQuestions(
            UUID documentId,
            QuestionScope scope,
            UUID slideId,
            String cursor,
            int size,
            User user
    ) {
        Document document =
                getDocument(documentId);

        requireMember(
                document.getSpace().getId(),
                user
        );

        if (scope == QuestionScope.SLIDE
                && slideId == null) {
            fail(QuestionErrorCode.INVALID_SCOPE);
        }

        if (slideId != null) {
            Slide slide = getSlide(slideId);

            if (!slide.getDocument()
                    .getId()
                    .equals(documentId)) {
                fail(QuestionErrorCode.SLIDE_NOT_FOUND);
            }
        }

        List<Question> all =
                (scope == QuestionScope.SLIDE
                        ? questions
                        .findAllBySlideIdAndDeletedFalse(
                                slideId
                        )
                        : questions
                        .findAllByDocumentIdAndDeletedFalse(
                                documentId
                        ))
                        .stream()
                        .sorted(
                                comparator(
                                        QuestionSortType.LATEST
                                )
                        )
                        .toList();

        int pageSize = size(size);
        int offset = offset(cursor);

        List<Question> page =
                all.stream()
                        .skip(offset)
                        .limit(pageSize)
                        .toList();

        boolean hasNext =
                offset + page.size() < all.size();

        return new DocumentListResponse(
                page.stream()
                        .map(this::documentItem)
                        .toList(),
                hasNext
                        ? cursorCodec.encode(
                                new OffsetCursor(
                                        offset + page.size()
                                )
                        )
                        : null,
                hasNext
        );
    }

    @Transactional
    public Detail detail(
            UUID id,
            User user
    ) {
        Question question =
                getQuestion(id);

        requireMember(
                question.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        boolean firstView = recentQuestionViews.insertIfAbsent(
                user.getId(),
                question.getId()
        ) == 1;

        int viewCount = question.getViewCount();
        if (firstView) {
            if (questions.increaseViewCount(question.getId()) == 0) {
                fail(QuestionErrorCode.QUESTION_NOT_FOUND);
            }
            viewCount++;
        } else {
            recentQuestionViews.refreshViewedAt(
                    user.getId(),
                    question.getId()
            );
        }

        return new Detail(
                question.getId(),
                question.getTitle(),
                question.getContent(),
                doc(question.getDocument()),
                slide(question.getSlide()),
                categoryInfo(question),
                question.getXRatio(),
                question.getYRatio(),
                viewCount,
                question.getLikeCount(),
                likes.existsByQuestionIdAndUserId(
                        id,
                        user.getId()
                ),
                question.getStatus(),
                answers
                        .findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(
                                id
                        )
                        .stream()
                        .map(this::answerInfo)
                        .toList(),
                comments
                        .findAllByQuestionIdAndDeletedFalseOrderByCreatedAtAsc(
                                id
                        )
                        .stream()
                        .map(this::commentInfo)
                        .toList()
        );
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Create createPinned(
            UUID slideId,
            QuestionCreateRequest request,
            User user
    ) {
        validatePin(
                request.xRatio(),
                request.yRatio()
        );

        UUID questionId =
                transactionTemplate.execute(status -> {

                    Slide slide =
                            getSlide(slideId);

                    requireStudent(
                            slide.getDocument()
                                    .getSpace()
                                    .getId(),
                            user
                    );

                    Question question =
                            Question.createWithPin(
                                    slide.getDocument(),
                                    slide,
                                    user,
                                    request.title().trim(),
                                    request.content().trim(),
                                    request.xRatio(),
                                    request.yRatio()
                            );

                    return questions
                            .save(question)
                            .getId();
                });

        if (questionId == null) {
            throw new IllegalStateException(
                    "Question creation transaction returned null."
            );
        }

        processAiSafely(questionId);

        return transactionTemplate.execute(status -> {

            Question question =
                    getQuestion(questionId);

            return new Create(
                    question.getId(),
                    question.getDocument().getId(),
                    question.getSlide().getId(),
                    question.getTitle(),
                    question.getContent(),
                    question.getXRatio(),
                    question.getYRatio(),
                    categoryInfo(question),
                    question.getStatus(),
                    question.getCreatedAt()
            );
        });
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SpaceCreate create(
            UUID spaceId,
            SpaceQuestionCreateRequest request,
            User user
    ) {
        UUID questionId =
                transactionTemplate.execute(status -> {

                    requireStudent(
                            spaceId,
                            user
                    );

                    Document document =
                            requireDocument(
                                    request.documentId(),
                                    spaceId
                            );

                    Question question =
                            Question.create(
                                    document,
                                    user,
                                    request.title().trim(),
                                    request.content().trim()
                            );

                    return questions
                            .save(question)
                            .getId();
                });

        if (questionId == null) {
            throw new IllegalStateException(
                    "Question creation transaction returned null."
            );
        }

        processAiSafely(questionId);

        return transactionTemplate.execute(status -> {

            Question question =
                    getQuestion(questionId);

            return new SpaceCreate(
                    question.getId(),
                    doc(question.getDocument()),
                    null,
                    question.getTitle(),
                    question.getContent(),
                    categoryInfo(question),
                    question.getStatus(),
                    question.getCreatedAt()
            );
        });
    }

    public SimilarResponse similar(
            UUID questionId,
            User user
    ) {
        Question source =
                getQuestion(questionId);

        requireMember(
                source.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        List<SimilarItem> found =
                similarQuestionService
                        .findSimilarQuestions(questionId)
                        .stream()
                        .map(result -> {
                            Question question =
                                    result.question();

                            return new SimilarItem(
                                    question.getId(),
                                    question.getTitle(),
                                    question.getContent(),
                                    categoryInfo(question),
                                    question.getStatus(),
                                    question.getLikeCount(),
                                    likes.existsByQuestionIdAndUserId(
                                            question.getId(),
                                            user.getId()
                                    ),
                                    result.similarity()
                            );
                        })
                        .toList();

        return new SimilarResponse(questionId, found);
    }

    @Transactional
    public Delete deleteQuestion(
            UUID id,
            User user
    ) {
        Question question =
                getQuestion(id);

        requireProfessor(
                question.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        question.delete();

        return new Delete(
                question.getId(),
                true,
                question.getDeletedAt()
        );
    }

    @Transactional
    public AnswerMutation addAnswer(
            UUID questionId,
            String content,
            User user
    ) {
        Question question =
                getQuestion(questionId);

        requireManager(
                question.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        Answer answer =
                answers.save(
                        Answer.create(
                                question,
                                user,
                                content.trim()
                        )
                );

        question.markAnswered();

        return new AnswerMutation(
                answer.getId(),
                questionId,
                answer.getContent(),
                answer.getCreatedAt(),
                answer.getUpdatedAt(),
                null
        );
    }

    @Transactional
    public AnswerMutation updateAnswer(
            UUID id,
            String content,
            User user
    ) {
        Answer answer =
                getAnswer(id);

        if (!answer.getAuthor()
                .getId()
                .equals(user.getId())) {
            fail(QuestionErrorCode.AUTHOR_ONLY);
        }

        answer.updateContent(
                content.trim()
        );

        return new AnswerMutation(
                answer.getId(),
                answer.getQuestion().getId(),
                answer.getContent(),
                answer.getCreatedAt(),
                Instant.now(),
                null
        );
    }

    @Transactional
    public AnswerMutation deleteAnswer(
            UUID id,
            User user
    ) {
        Answer answer =
                getAnswer(id);

        SpaceMember member =
                requireMember(
                        answer.getQuestion()
                                .getDocument()
                                .getSpace()
                                .getId(),
                        user
                );

        if (!answer.getAuthor()
                .getId()
                .equals(user.getId())
                && member.getRole()
                != SpaceMemberRole.PROFESSOR) {

            fail(QuestionErrorCode.AUTHOR_ONLY);
        }

        answer.delete();

        if (answers.countByQuestionIdAndDeletedFalse(
                answer.getQuestion().getId()
        ) == 0) {
            answer.getQuestion().markPending();
        }

        return new AnswerMutation(
                answer.getId(),
                answer.getQuestion().getId(),
                answer.getContent(),
                answer.getCreatedAt(),
                answer.getUpdatedAt(),
                true
        );
    }

    @Transactional
    public CommentMutation addComment(
            UUID questionId,
            CommentCreateRequest request,
            User user
    ) {
        Question question =
                getQuestion(questionId);

        requireManager(
                question.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        QuestionComment parent =
                request.parentCommentId() == null
                        ? null
                        : getComment(
                                request.parentCommentId()
                        );

        if (parent != null
                && !parent.getQuestion()
                .getId()
                .equals(questionId)) {

            fail(
                    QuestionErrorCode.COMMENT_NOT_FOUND
            );
        }

        QuestionComment comment =
                comments.save(
                        QuestionComment.create(
                                question,
                                user,
                                parent,
                                request.content().trim()
                        )
                );

        return commentMutation(
                comment,
                null
        );
    }

    @Transactional
    public CommentMutation updateComment(
            UUID id,
            String content,
            User user
    ) {
        QuestionComment comment =
                getComment(id);

        if (!comment.getAuthor()
                .getId()
                .equals(user.getId())) {

            fail(QuestionErrorCode.AUTHOR_ONLY);
        }

        comment.updateContent(
                content.trim()
        );

        return commentMutation(
                comment,
                null
        );
    }

    @Transactional
    public CommentMutation deleteComment(
            UUID id,
            User user
    ) {
        QuestionComment comment =
                getComment(id);

        SpaceMember member =
                requireMember(
                        comment.getQuestion()
                                .getDocument()
                                .getSpace()
                                .getId(),
                        user
                );

        if (!comment.getAuthor()
                .getId()
                .equals(user.getId())
                && member.getRole()
                != SpaceMemberRole.PROFESSOR) {

            fail(QuestionErrorCode.AUTHOR_ONLY);
        }

        comment.delete();

        return commentMutation(
                comment,
                true
        );
    }

    @Transactional
    public Like like(
            UUID id,
            User user
    ) {
        Question question =
                getQuestionForUpdate(id);

        requireMember(
                question.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        if (likes.existsByQuestionIdAndUserId(
                id,
                user.getId()
        )) {
            fail(
                    QuestionErrorCode.LIKE_ALREADY_EXISTS
            );
        }

        likes.save(
                QuestionLike.create(
                        question,
                        user
                )
        );

        question.increaseLikeCount();

        return new Like(
                id,
                true,
                question.getLikeCount()
        );
    }

    @Transactional
    public Like unlike(
            UUID id,
            User user
    ) {
        Question question =
                getQuestionForUpdate(id);

        requireMember(
                question.getDocument()
                        .getSpace()
                        .getId(),
                user
        );

        if (!likes.existsByQuestionIdAndUserId(
                id,
                user.getId()
        )) {
            fail(
                    QuestionErrorCode.LIKE_NOT_FOUND
            );
        }

        likes.deleteByQuestionIdAndUserId(
                id,
                user.getId()
        );

        question.decreaseLikeCount();

        return new Like(
                id,
                false,
                question.getLikeCount()
        );
    }

    public CategoriesResponse categoryList(
            UUID spaceId,
            User user
    ) {
        requireMember(
                spaceId,
                user
        );

        return new CategoriesResponse(
                documents
                        .findAllBySpaceIdOrderByCreatedAtDescIdDesc(
                                spaceId
                        )
                        .stream()
                        .map(document ->
                                new DocumentCategories(
                                        document.getId(),
                                        document.getTitle(),
                                        categories
                                                .findAllByDocumentIdAndDeletedFalse(
                                                        document.getId()
                                                )
                                                .stream()
                                                .map(category ->
                                                        new CategoryItem(
                                                                category.getId(),
                                                                category.getName(),
                                                                category.getCreatedBy()
                                                                        == null
                                                                        ? "AI"
                                                                        : "MANUAL"
                                                        )
                                                )
                                                .toList()
                                )
                        )
                        .toList()
        );
    }

    /**
     * 이전 프론트엔드가 사용하는 카테고리 일괄 저장 API 호환용 구현이다.
     */
    @Transactional
    public CategoryBatchResponse saveCategories(
            UUID spaceId,
            CategoryBatchRequest request,
            User user
    ) {
        requireManager(spaceId, user);

        List<CategoryResult> results = new ArrayList<>();

        for (CategoryBatchRequest.Operation operation : request.operations()) {
            Document document = requireDocument(operation.documentId(), spaceId);
            QuestionCategory category;

            switch (operation.type()) {
                case CREATE -> {
                    String categoryName = name(operation.name());

                    if (categories.existsByDocumentIdAndNameAndDeletedFalse(
                            document.getId(), categoryName)) {
                        fail(QuestionErrorCode.CATEGORY_DUPLICATED);
                    }

                    category = categories.save(
                            QuestionCategory.createManual(document, categoryName, user));
                }
                case UPDATE -> {
                    category = getCategory(operation.categoryId(), document.getId());
                    String categoryName = name(operation.name());

                    if (!category.getName().equals(categoryName)
                            && categories.existsByDocumentIdAndNameAndDeletedFalse(
                                    document.getId(), categoryName)) {
                        fail(QuestionErrorCode.CATEGORY_DUPLICATED);
                    }

                    category.updateName(categoryName);
                }
                case DELETE -> {
                    category = getCategory(operation.categoryId(), document.getId());
                    category.delete();
                    mappings.deleteAllByCategoryId(category.getId());
                }
                default -> throw new BusinessException(
                        QuestionErrorCode.INVALID_CATEGORY_OPERATION);
            }

            results.add(new CategoryResult(
                    operation.operationId(),
                    operation.type().name(),
                    document.getId(),
                    operation.tempId(),
                    category.getId(),
                    category.getName(),
                    "SUCCESS"
            ));

            if (operation.type() != CategoryBatchRequest.Type.DELETE) {
                categoryQuestionRemappingService.scheduleRecalculation(document.getId());
            }
        }

        return new CategoryBatchResponse(results, Instant.now());
    }

    @Transactional
    public CategoryMutation createCategory(
            UUID documentId,
            CategoryCreateRequest request,
            User user
    ) {
        Document document = getDocument(documentId);
        requireManager(document.getSpace().getId(), user);
        String categoryName = name(request.name());

        if (categories.existsByDocumentIdAndNameAndDeletedFalse(documentId, categoryName)) {
            fail(QuestionErrorCode.CATEGORY_DUPLICATED);
        }

        QuestionCategory category = categories.save(
                QuestionCategory.createManual(document, categoryName, user)
        );

        categoryQuestionRemappingService.scheduleRecalculation(documentId);

        return new CategoryMutation(
                category.getId(), documentId, category.getName(), category.getSourceType().name()
        );
    }

    @Transactional
    public CategoryMutation updateCategory(
            UUID categoryId,
            CategoryUpdateRequest request,
            User user
    ) {
        QuestionCategory category = categories.findById(categoryId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(QuestionErrorCode.CATEGORY_NOT_FOUND));

        requireManager(category.getDocument().getSpace().getId(), user);
        String categoryName = name(request.name());

        if (!category.getName().equals(categoryName)
                && categories.existsByDocumentIdAndNameAndDeletedFalse(
                        category.getDocument().getId(), categoryName)) {
            fail(QuestionErrorCode.CATEGORY_DUPLICATED);
        }

        category.updateName(categoryName);
        categoryQuestionRemappingService.scheduleRecalculation(
                category.getDocument().getId()
        );

        return new CategoryMutation(
                category.getId(), category.getDocument().getId(), category.getName(), category.getSourceType().name()
        );
    }

    @Transactional
    public CategoryMutation deleteCategory(
            UUID categoryId,
            User user
    ) {
        QuestionCategory category = categories.findById(categoryId)
                .filter(value -> !value.isDeleted())
                .orElseThrow(() -> new BusinessException(QuestionErrorCode.CATEGORY_NOT_FOUND));

        requireManager(category.getDocument().getSpace().getId(), user);
        category.delete();
        mappings.deleteAllByCategoryId(categoryId);

        return new CategoryMutation(
                category.getId(), category.getDocument().getId(), category.getName(), category.getSourceType().name()
        );
    }

    @Transactional
    public QuestionCategoryMutation createQuestionCategory(
            UUID questionId,
            CategoryCreateRequest request,
            User user
    ) {
        Question question = getQuestion(questionId);
        Document document = question.getDocument();
        requireManager(document.getSpace().getId(), user);

        String categoryName = name(request.name());
        if (categories.existsByDocumentIdAndNameAndDeletedFalse(
                document.getId(), categoryName)) {
            fail(QuestionErrorCode.CATEGORY_DUPLICATED);
        }

        QuestionCategory category = categories.save(
                QuestionCategory.createManual(document, categoryName, user)
        );
        mappings.save(QuestionCategoryMapping.create(question, category));

        categoryQuestionRemappingService.scheduleRecalculation(
                document.getId(), questionId
        );

        return new QuestionCategoryMutation(
                questionId,
                category.getId(),
                document.getId(),
                category.getName(),
                category.getSourceType().name()
        );
    }

    @Transactional
    public QuestionCategoryMutation deleteQuestionCategory(
            UUID questionId,
            UUID categoryId,
            User user
    ) {
        Question question = getQuestion(questionId);
        Document document = question.getDocument();
        requireManager(document.getSpace().getId(), user);

        QuestionCategory category = getCategory(categoryId, document.getId());
        mappings.findAllByQuestionId(questionId).stream()
                .filter(mapping -> mapping.getCategory().getId().equals(categoryId))
                .forEach(mappings::delete);

        return new QuestionCategoryMutation(
                questionId,
                category.getId(),
                document.getId(),
                category.getName(),
                category.getSourceType().name()
        );
    }

    public CategorizedQuestionsResponse categorizedQuestions(
            UUID documentId,
            User user
    ) {
        Document document = getDocument(documentId);
        requireMember(document.getSpace().getId(), user);

        List<CategoryGroup> groups = categories.findAllByDocumentIdAndDeletedFalse(documentId)
                .stream()
                .map(category -> new CategoryGroup(
                        category.getId(),
                        category.getName(),
                        mappings.findAllByCategoryId(category.getId()).stream()
                                .map(mapping -> mapping.getQuestion())
                                .filter(question -> !question.isDeleted())
                                .filter(question -> question.getQuestionScope() == com.tikitaka.question.entity.QuestionScope.COURSE_RELATED)
                                .sorted(Comparator.comparing(Question::getCreatedAt).reversed())
                                .map(question -> new CategorizedQuestion(
                                        question.getId(),
                                        question.getTitle(),
                                        question.getContent(),
                                        question.getStatus(),
                                        question.getLikeCount()
                                ))
                                .toList()
                ))
                .toList();

        return new CategorizedQuestionsResponse(documentId, groups);
    }

    public ExportResponse export(
            UUID spaceId,
            String format,
            User user
    ) {
        requireManager(
                spaceId,
                user
        );

        if (!"csv".equalsIgnoreCase(format)) {
            fail(
                    QuestionErrorCode
                            .INVALID_CATEGORY_OPERATION
            );
        }

        StringBuilder csv =
                new StringBuilder(
                        "question_id,title,content,status,like_count,view_count\n"
                );

        questions
                .findAllByDocumentSpaceIdAndDeletedFalse(
                        spaceId
                )
                .forEach(question ->
                        csv.append(question.getId())
                                .append(',')
                                .append(
                                        quote(
                                                question.getTitle()
                                        )
                                )
                                .append(',')
                                .append(
                                        quote(
                                                question.getContent()
                                        )
                                )
                                .append(',')
                                .append(
                                        question.getStatus()
                                )
                                .append(',')
                                .append(
                                        question.getLikeCount()
                                )
                                .append(',')
                                .append(
                                        question.getViewCount()
                                )
                                .append('\n')
                );

        return new ExportResponse(
                "data:text/csv;base64,"
                        + Base64
                        .getEncoder()
                        .encodeToString(
                                csv.toString()
                                        .getBytes(
                                                StandardCharsets.UTF_8
                                        )
                        )
        );
    }

    private void processAiSafely(
            UUID questionId
    ) {
        try {
            questionAiProcessingService.process(
                    questionId,
                    null,
                    null
            );

        } catch (Exception exception) {

            log.warn(
                    "Question AI processing failed. questionId={}",
                    questionId,
                    exception
            );
        }
    }

    private ListItem listItem(
            Question question,
            User user
    ) {
        return new ListItem(
                question.getId(),
                question.getTitle(),
                doc(question.getDocument()),
                slide(question.getSlide()),
                categoryInfo(question),
                question.getCreatedAt(),
                question.getViewCount(),
                question.getLikeCount(),
                likes.existsByQuestionIdAndUserId(
                        question.getId(),
                        user.getId()
                ),
                question.getStatus()
        );
    }

    private DocumentListItem documentItem(
            Question question
    ) {
        return new DocumentListItem(
                question.getId(),
                question.getTitle(),
                question.getContent(),
                slide(question.getSlide()),
                categoryInfo(question),
                question.getXRatio(),
                question.getYRatio(),
                question.getLikeCount(),
                question.getStatus()
        );
    }

    private DocumentInfo doc(
            Document document
    ) {
        return new DocumentInfo(
                document.getId(),
                document.getTitle()
        );
    }

    private SlideInfo slide(
            Slide slide
    ) {
        return slide == null
                ? null
                : new SlideInfo(
                        slide.getId(),
                        slide.getPageNumber(),
                        slide.getThumbnailKey()
                );
    }

    private List<CategoryInfo> categoryInfo(
            Question question
    ) {
        return mappings
                .findAllByQuestionId(
                        question.getId()
                )
                .stream()
                .filter(mapping ->
                        !mapping.getCategory()
                                .isDeleted()
                )
                .map(mapping ->
                        new CategoryInfo(
                                mapping.getCategory()
                                        .getId(),
                                mapping.getCategory()
                                        .getName()
                        )
                )
                .toList();
    }

    private AuthorInfo author(
            User user
    ) {
        return new AuthorInfo(
                user.getId(),
                user.getName(),
                user.getProfileUrl()
        );
    }

    private AnswerInfo answerInfo(
            Answer answer
    ) {
        return new AnswerInfo(
                answer.getId(),
                author(answer.getAuthor()),
                answer.getContent(),
                answer.getCreatedAt(),
                answer.getUpdatedAt(),
                answer.getAnswerType(),
                answer.getTranscript()
        );
    }

    private CommentInfo commentInfo(
            QuestionComment comment
    ) {
        return new CommentInfo(
                comment.getId(),
                comment.getParentComment() == null
                        ? null
                        : comment.getParentComment()
                        .getId(),
                author(comment.getAuthor()),
                comment.getContent(),
                comment.getCreatedAt(),
                comment.getUpdatedAt()
        );
    }

    private CommentMutation commentMutation(
            QuestionComment comment,
            Boolean deleted
    ) {
        return new CommentMutation(
                comment.getId(),
                comment.getQuestion().getId(),
                comment.getParentComment() == null
                        ? null
                        : comment.getParentComment()
                        .getId(),
                comment.getContent(),
                comment.getCreatedAt(),
                deleted == null
                        ? Instant.now()
                        : comment.getUpdatedAt(),
                deleted
        );
    }

    private Comparator<Question> comparator(
            QuestionSortType type
    ) {
        Comparator<Question> tie =
                Comparator
                        .comparing(
                                Question::getCreatedAt
                        )
                        .thenComparing(
                                Question::getId
                        )
                        .reversed();

        return switch (
                type == null
                        ? QuestionSortType.LATEST
                        : type
        ) {
            case MOST_VIEWED ->
                    Comparator
                            .comparing(
                                    Question::getViewCount
                            )
                            .reversed()
                            .thenComparing(tie);

            case MOST_POPULAR ->
                    Comparator
                            .comparing(
                                    Question::getLikeCount
                            )
                            .reversed()
                            .thenComparing(tie);

            case LATEST -> tie;
        };
    }

    private int size(
            int size
    ) {
        return size <= 0
                ? DEFAULT_SIZE
                : Math.min(
                        size,
                        MAX_SIZE
                );
    }

    private int offset(
            String cursor
    ) {
        OffsetCursor decoded =
                cursorCodec.decodeOrNull(
                        cursor,
                        OffsetCursor.class
                );

        return decoded == null
                ? 0
                : Math.max(
                        0,
                        decoded.offset()
                );
    }

    private SpaceMember requireMember(
            UUID spaceId,
            User user
    ) {
        return members
                .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                        spaceId,
                        user.getId(),
                        SpaceMemberStatus.APPROVED
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .SPACE_MEMBER_REQUIRED
                        )
                );
    }

    private SpaceMember requireStudent(
            UUID spaceId,
            User user
    ) {
        SpaceMember member =
                requireMember(
                        spaceId,
                        user
                );

        if (member.getRole()
                != SpaceMemberRole.STUDENT) {
            fail(
                    QuestionErrorCode.STUDENT_ONLY
            );
        }

        return member;
    }

    private SpaceMember requireManager(
            UUID spaceId,
            User user
    ) {
        SpaceMember member =
                requireMember(
                        spaceId,
                        user
                );

        if (member.getRole()
                != SpaceMemberRole.PROFESSOR
                && !(member.getRole()
                == SpaceMemberRole.ASSISTANT
                && permissions
                .existsBySpaceMemberIdAndPermission(
                        member.getId(),
                        PermissionType.QUESTION_MANAGE
                ))) {

            fail(
                    QuestionErrorCode
                            .QUESTION_MANAGE_FORBIDDEN
            );
        }

        return member;
    }

    private void requireProfessor(
            UUID spaceId,
            User user
    ) {
        if (requireMember(
                spaceId,
                user
        ).getRole()
                != SpaceMemberRole.PROFESSOR) {

            fail(
                    QuestionErrorCode
                            .QUESTION_MANAGE_FORBIDDEN
            );
        }
    }

    private Question getQuestion(
            UUID id
    ) {
        return questions
                .findById(id)
                .filter(question ->
                        !question.isDeleted()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .QUESTION_NOT_FOUND
                        )
                );
    }

    private Question getQuestionForUpdate(
            UUID id
    ) {
        return questions
                .findQuestionById(id)
                .filter(question ->
                        !question.isDeleted()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .QUESTION_NOT_FOUND
                        )
                );
    }

    private Answer getAnswer(
            UUID id
    ) {
        return answers
                .findById(id)
                .filter(answer ->
                        !answer.isDeleted()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .ANSWER_NOT_FOUND
                        )
                );
    }

    private QuestionComment getComment(
            UUID id
    ) {
        return comments
                .findById(id)
                .filter(comment ->
                        !comment.isDeleted()
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .COMMENT_NOT_FOUND
                        )
                );
    }

    private Document getDocument(
            UUID id
    ) {
        return documents
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .DOCUMENT_NOT_FOUND
                        )
                );
    }

    private Document requireDocument(
            UUID id,
            UUID spaceId
    ) {
        Document document =
                getDocument(id);

        if (!document.getSpace()
                .getId()
                .equals(spaceId)) {

            fail(
                    QuestionErrorCode
                            .DOCUMENT_NOT_FOUND
            );
        }

        return document;
    }

    private Slide getSlide(
            UUID id
    ) {
        return slides
                .findById(id)
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .SLIDE_NOT_FOUND
                        )
                );
    }

    private QuestionCategory getCategory(
            UUID id,
            UUID documentId
    ) {
        if (id == null) {
            fail(
                    QuestionErrorCode
                            .INVALID_CATEGORY_OPERATION
            );
        }

        return categories
                .findById(id)
                .filter(category ->
                        !category.isDeleted()
                                && category
                                .getDocument()
                                .getId()
                                .equals(documentId)
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .CATEGORY_NOT_FOUND
                        )
                );
    }

    private QuestionCategory requireCategoryInScope(
            UUID categoryId,
            UUID spaceId,
            UUID documentId
    ) {
        return categories
                .findById(categoryId)
                .filter(category ->
                        !category.isDeleted()
                                && category.getDocument()
                                .getSpace()
                                .getId()
                                .equals(spaceId)
                                && (documentId == null
                                || category.getDocument()
                                .getId()
                                .equals(documentId))
                )
                .orElseThrow(() -> new BusinessException(
                        QuestionErrorCode.CATEGORY_NOT_FOUND));
    }

    private String name(
            String value
    ) {
        if (value == null
                || value.isBlank()) {

            fail(
                    QuestionErrorCode
                            .INVALID_CATEGORY_OPERATION
            );
        }

        return value.trim();
    }

    private void validatePin(
            double x,
            double y
    ) {
        if (x < 0
                || x > 1
                || y < 0
                || y > 1) {

            fail(
                    QuestionErrorCode.INVALID_PIN
            );
        }
    }

    private static void fail(
            QuestionErrorCode errorCode
    ) {
        throw new BusinessException(
                errorCode
        );
    }

    private String quote(
            String value
    ) {
        return "\""
                + value
                .replace(
                        "\"",
                        "\"\""
                )
                .replace(
                        "\r",
                        " "
                )
                .replace(
                        "\n",
                        " "
                )
                + "\"";
    }
}
