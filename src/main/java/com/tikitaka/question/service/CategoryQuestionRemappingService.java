package com.tikitaka.question.service;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.tikitaka.question.ai.QuestionAiClient;
import com.tikitaka.question.ai.dto.QuestionAnalyzeRequest;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCategoryMapping;
import com.tikitaka.question.entity.QuestionCategoryMappingId;
import com.tikitaka.question.entity.QuestionScope;
import com.tikitaka.question.repository.QuestionCategoryMappingRepository;
import com.tikitaka.question.repository.QuestionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryQuestionRemappingService {

    private final QuestionRepository questionRepository;
    private final QuestionCategoryMappingRepository mappingRepository;
    private final QuestionAiClient questionAiClient;
    private final QuestionAiContextService contextService;
    private final TransactionTemplate transactionTemplate;

    @Value("${question.ai.category-confidence-threshold:0.75}")
    private double categoryConfidenceThreshold;

    public void recalculate(QuestionCategory category) {
        UUID documentId = category.getDocument().getId();

        List<Question> questions = questionRepository
                .findAllByDocumentIdAndDeletedFalse(documentId)
                .stream()
                .filter(q -> q.getQuestionScope() == QuestionScope.COURSE_RELATED)
                .toList();

        for (Question question : questions) {
            recalculateOne(question, category);
        }
    }

    private void recalculateOne(Question question, QuestionCategory category) {
        QuestionAiContextService.Context context = contextService.load(
                question.getDocument().getId(),
                question.getSlide() == null ? null : question.getSlide().getId()
        );

        QuestionAnalyzeRequest request = new QuestionAnalyzeRequest(
                question.getId(),
                question.getTitle(),
                question.getContent(),
                context.slideContext(),
                context.documentContext(),
                List.of(new QuestionAnalyzeRequest.CategoryCandidate(category.getId(), category.getName()))
        );

        var response = questionAiClient.analyzeQuestion(request);
        boolean matched = response.relation() == QuestionScope.COURSE_RELATED
                && response.categories() != null
                && response.categories().stream().anyMatch(result ->
                        result.categoryId().equals(category.getId())
                                && result.confidence() >= categoryConfidenceThreshold
                );

        transactionTemplate.executeWithoutResult(status -> {
            QuestionCategoryMappingId id = new QuestionCategoryMappingId(question.getId(), category.getId());
            if (matched) {
                if (!mappingRepository.existsById(id)) {
                    mappingRepository.save(QuestionCategoryMapping.create(question, category));
                }
            } else if (mappingRepository.existsById(id)) {
                mappingRepository.deleteById(id);
            }
        });
    }
}
