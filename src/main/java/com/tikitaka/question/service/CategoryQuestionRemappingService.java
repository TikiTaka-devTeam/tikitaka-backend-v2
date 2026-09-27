package com.tikitaka.question.service;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionScope;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class CategoryQuestionRemappingService {

    private final QuestionRepository questionRepository;
    private final QuestionCategoryRepository categoryRepository;
    private final QuestionCategoryMappingRepository mappingRepository;
    private final QuestionAiClient questionAiClient;
    private final QuestionAiContextService contextService;
    private final TransactionTemplate transactionTemplate;
    private final TaskExecutor taskExecutor;

    @Value("${question.ai.category-confidence-threshold:0.75}")
    private double categoryConfidenceThreshold;

    public CategoryQuestionRemappingService(
            QuestionRepository questionRepository,
            QuestionCategoryRepository categoryRepository,
            QuestionCategoryMappingRepository mappingRepository,
            QuestionAiClient questionAiClient,
            QuestionAiContextService contextService,
            TransactionTemplate transactionTemplate,
            @Qualifier("applicationTaskExecutor")
            TaskExecutor taskExecutor
    ) {
        this.questionRepository = questionRepository;
        this.categoryRepository = categoryRepository;
        this.mappingRepository = mappingRepository;
        this.questionAiClient = questionAiClient;
        this.contextService = contextService;
        this.transactionTemplate = transactionTemplate;
        this.taskExecutor = taskExecutor;
    }

    /**
     * 문서의 카테고리 변경이 커밋된 뒤에만 한 번의 재분류 작업을 제출한다.
     */
    public void scheduleRecalculation(
            UUID documentId
    ) {
        scheduleRecalculation(documentId, null);
    }

    public void scheduleRecalculation(
            UUID documentId,
            UUID excludedQuestionId
    ) {
        Runnable submitTask = () ->
                taskExecutor.execute(
                        () -> runSafely(documentId, excludedQuestionId)
                );

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            submitTask.run();
                        }
                    }
            );
            return;
        }

        submitTask.run();
    }

    private void runSafely(
            UUID documentId,
            UUID excludedQuestionId
    ) {
        try {
            recalculate(documentId, excludedQuestionId);
        } catch (RuntimeException exception) {
            log.warn(
                    "Question category remapping failed. documentId={}",
                    documentId,
                    exception
            );
        }
    }

    private void recalculate(
            UUID documentId,
            UUID excludedQuestionId
    ) {
        RemappingSnapshot snapshot = transactionTemplate.execute(
                status -> loadSnapshot(documentId)
        );

        if (snapshot == null || snapshot.categories().isEmpty()) {
            return;
        }

        for (QuestionSnapshot question : snapshot.questions().stream()
                .filter(question -> !question.questionId().equals(excludedQuestionId))
                .toList()) {
            recalculateOne(question, snapshot);
        }
    }

    private RemappingSnapshot loadSnapshot(
            UUID documentId
    ) {
        List<CategorySnapshot> categories = categoryRepository
                .findAllByDocumentIdAndDeletedFalse(documentId)
                .stream()
                .map(category -> new CategorySnapshot(
                        category.getId(),
                        category.getName(),
                        category.getDescription()
                ))
                .toList();

        List<QuestionSnapshot> questions = questionRepository
                .findAllByDocumentIdAndDeletedFalse(documentId)
                .stream()
                .filter(question -> question.getQuestionScope() == QuestionScope.COURSE_RELATED)
                .map(question -> new QuestionSnapshot(
                        question.getId(),
                        question.getTitle(),
                        question.getContent(),
                        question.getSlide() == null ? null : question.getSlide().getId()
                ))
                .toList();

        return new RemappingSnapshot(documentId, categories, questions);
    }

    private void recalculateOne(
            QuestionSnapshot question,
            RemappingSnapshot snapshot
    ) {
        QuestionAiContextService.Context context = contextService.load(
                snapshot.documentId(),
                question.slideId()
        );

        List<QuestionAnalyzeRequest.CategoryCandidate> candidates = snapshot.categories()
                .stream()
                .map(category -> new QuestionAnalyzeRequest.CategoryCandidate(
                        category.categoryId(),
                        category.name(),
                        category.description()
                ))
                .toList();

        QuestionAnalyzeRequest request = new QuestionAnalyzeRequest(
                question.questionId(),
                question.title(),
                question.content(),
                context.slideContext(),
                context.documentContext(),
                candidates
        );

        var response = questionAiClient.analyzeQuestion(request);
        Set<UUID> matchedCategoryIds = response.relation() == QuestionScope.COURSE_RELATED
                && response.categories() != null
                ? response.categories().stream()
                        .filter(result -> result.confidence() >= categoryConfidenceThreshold)
                        .map(result -> result.categoryId())
                        .collect(Collectors.toSet())
                : Set.of();

        transactionTemplate.executeWithoutResult(status ->
                replaceMappings(
                        question.questionId(),
                        snapshot.documentId(),
                        matchedCategoryIds
                )
        );
    }

    private void replaceMappings(
            UUID questionId,
            UUID documentId,
            Set<UUID> matchedCategoryIds
    ) {
        Question question = questionRepository.findById(questionId)
                .filter(value -> !value.isDeleted())
                .orElse(null);

        if (question == null) {
            return;
        }

        List<QuestionCategory> categories = categoryRepository
                .findAllByDocumentIdAndDeletedFalse(documentId);

        mappingRepository.deleteAllByQuestionId(questionId);

        categories.stream()
                .filter(category -> matchedCategoryIds.contains(category.getId()))
                .map(category -> QuestionCategoryMapping.create(question, category))
                .forEach(mappingRepository::save);
    }

    private record CategorySnapshot(
            UUID categoryId,
            String name,
            String description
    ) {
    }

    private record QuestionSnapshot(
            UUID questionId,
            String title,
            String content,
            UUID slideId
    ) {
    }

    private record RemappingSnapshot(
            UUID documentId,
            List<CategorySnapshot> categories,
            List<QuestionSnapshot> questions
    ) {
    }
}
