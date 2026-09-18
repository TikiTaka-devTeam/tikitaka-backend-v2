package com.tikitaka.question.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.document.entity.Document;
import com.tikitaka.document.repository.DocumentRepository;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.question.dto.response.QuestionClusterResponse;
import com.tikitaka.question.dto.response.QuestionClusterResponse.CategoryItem;
import com.tikitaka.question.dto.response.QuestionClusterResponse.ClusterItem;
import com.tikitaka.question.dto.response.QuestionClusterResponse.QuestionItem;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCluster;
import com.tikitaka.question.entity.QuestionClusterMember;
import com.tikitaka.question.exception.QuestionErrorCode;
import com.tikitaka.question.repository.QuestionCategoryRepository;
import com.tikitaka.question.repository.QuestionClusterMemberRepository;
import com.tikitaka.question.repository.QuestionClusterRepository;
import com.tikitaka.space.entity.SpaceMemberStatus;
import com.tikitaka.space.repository.SpaceMemberRepository;
import com.tikitaka.user.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuestionClusterQueryService {

    private final DocumentRepository documentRepository;

    private final QuestionCategoryRepository
            questionCategoryRepository;

    private final QuestionClusterRepository
            questionClusterRepository;

    private final QuestionClusterMemberRepository
            questionClusterMemberRepository;

    private final SpaceMemberRepository
            spaceMemberRepository;

    public QuestionClusterResponse getClusters(
            UUID documentId,
            User user
    ) {
        Document document =
                getDocument(
                        documentId
                );

        requireMember(
                document,
                user
        );

        List<QuestionCategory> categories =
                questionCategoryRepository
                        .findAllByDocumentIdAndDeletedFalse(
                                documentId
                        );

        List<QuestionCluster> clusters =
                questionClusterRepository
                        .findAllByDocumentIdOrderByCreatedAtAsc(
                                documentId
                        );

        List<CategoryItem> categoryItems =
                categories.stream()
                        .map(category ->
                                toCategoryItem(
                                        category,
                                        clusters
                                )
                        )
                        .toList();

        return new QuestionClusterResponse(
                documentId,
                categoryItems
        );
    }

    private CategoryItem toCategoryItem(
            QuestionCategory category,
            List<QuestionCluster> clusters
    ) {
        List<ClusterItem> clusterItems =
                clusters.stream()
                        .filter(cluster ->
                                cluster.getCategory()
                                        .getId()
                                        .equals(
                                                category.getId()
                                        )
                        )
                        .map(this::toClusterItem)
                        .toList();

        return new CategoryItem(
                category.getId(),
                category.getName(),
                clusterItems
        );
    }

    private ClusterItem toClusterItem(
            QuestionCluster cluster
    ) {
        List<QuestionClusterMember> members =
                questionClusterMemberRepository
                        .findAllByClusterIdOrderByCreatedAtAsc(
                                cluster.getId()
                        );

        List<QuestionItem> questions =
                members.stream()
                        .filter(member ->
                                !member.getQuestion()
                                        .isDeleted()
                        )
                        .map(this::toQuestionItem)
                        .toList();

        return new ClusterItem(
                cluster.getId(),
                cluster.getSummaryTitle(),
                cluster.getMemberCount(),
                questions
        );
    }

    private QuestionItem toQuestionItem(
            QuestionClusterMember member
    ) {
        var question =
                member.getQuestion();

        Double similarity =
                member.getSimilarity() == null
                        ? null
                        : member.getSimilarity()
                        .doubleValue();

        return new QuestionItem(
                question.getId(),
                question.getTitle(),
                question.getContent(),
                question.getStatus(),
                question.getLikeCount(),
                similarity,
                member.isRepresentative()
        );
    }

    private Document getDocument(
            UUID documentId
    ) {
        return documentRepository
                .findById(
                        documentId
                )
                .orElseThrow(() ->
                        new BusinessException(
                                QuestionErrorCode
                                        .DOCUMENT_NOT_FOUND
                        )
                );
    }

    private void requireMember(
            Document document,
            User user
    ) {
        UUID spaceId =
                document.getSpace()
                        .getId();

        boolean memberExists =
                spaceMemberRepository
                        .findBySpaceIdAndUserIdAndStatusAndRemovedAtIsNull(
                                spaceId,
                                user.getId(),
                                SpaceMemberStatus.APPROVED
                        )
                        .isPresent();

        if (!memberExists) {
            throw new BusinessException(
                    QuestionErrorCode
                            .SPACE_MEMBER_REQUIRED
            );
        }
    }
}