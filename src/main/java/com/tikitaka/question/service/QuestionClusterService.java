package com.tikitaka.question.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.tikitaka.document.entity.Document;
import com.tikitaka.question.entity.Question;
import com.tikitaka.question.entity.QuestionCategory;
import com.tikitaka.question.entity.QuestionCluster;
import com.tikitaka.question.entity.QuestionClusterMember;
import com.tikitaka.question.repository.QuestionClusterMemberRepository;
import com.tikitaka.question.repository.QuestionClusterRepository;
import com.tikitaka.question.repository.projection.ClusterSimilarityProjection;
import com.tikitaka.question.util.VectorUtils;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuestionClusterService {

    private static final double CLUSTER_THRESHOLD = 0.80;

    private final QuestionClusterRepository questionClusterRepository;
    private final QuestionClusterMemberRepository questionClusterMemberRepository;

    /**
     * 현재 질문과 가장 유사한 기존 Cluster를 조회한다.
     *
     * 검색 범위:
     * - 동일 document
     * - 동일 primary category
     *
     * Threshold 이상인 Cluster가 있을 때만 반환한다.
     */
    @Transactional(readOnly = true)
    public Optional<QuestionCluster> findMatchingCluster(
            UUID documentId,
            UUID categoryId,
            float[] embedding
    ) {
        validateEmbedding(embedding);

        String pgVector = VectorUtils.toPgVector(embedding);

        Optional<ClusterSimilarityProjection> result =
                questionClusterRepository.findBestCluster(
                        documentId,
                        categoryId,
                        pgVector
                );

        if (result.isEmpty()) {
            return Optional.empty();
        }

        ClusterSimilarityProjection best = result.get();

        if (best.getSimilarity() == null
                || best.getSimilarity() < CLUSTER_THRESHOLD) {
            return Optional.empty();
        }

        return questionClusterRepository.findById(best.getClusterId());
    }

    /**
     * 기존 Cluster에 질문을 배정한다.
     *
     * Member 추가와
     * centroid / memberCount 갱신을
     * 동일 Transaction에서 처리한다.
     */
    @Transactional
    public void assignToExistingCluster(
            QuestionCluster cluster,
            Question question,
            float[] embedding
    ) {
        validateEmbedding(embedding);

        double similarity = calculateSimilarity(
                cluster.getCentroid(),
                embedding
        );

        QuestionClusterMember member =
                QuestionClusterMember.createMember(
                        cluster,
                        question,
                        (float) similarity
                );

        questionClusterMemberRepository.save(member);

        cluster.addMember(embedding);
    }

    /**
     * 신규 Cluster를 생성한다.
     *
     * summaryTitle은 이후 Python
     * /ai/clusters/title 결과를 전달받아 사용한다.
     */
    @Transactional
    public QuestionCluster createNewCluster(
            Document document,
            QuestionCategory category,
            Question question,
            String summaryTitle,
            float[] embedding
    ) {
        validateEmbedding(embedding);

        QuestionCluster cluster =
                QuestionCluster.create(
                        document,
                        category,
                        summaryTitle,
                        embedding
                );

        QuestionCluster savedCluster =
                questionClusterRepository.save(cluster);

        QuestionClusterMember representative =
                QuestionClusterMember.createRepresentative(
                        savedCluster,
                        question
                );

        questionClusterMemberRepository.save(representative);

        return savedCluster;
    }

    /**
     * Java 내부에서 필요한 경우 사용할 Cosine Similarity 계산.
     *
     * Cluster 후보 검색 자체는 PostgreSQL pgvector에서 수행한다.
     */
    private double calculateSimilarity(
            float[] vectorA,
            float[] vectorB
    ) {
        validateEmbedding(vectorA);
        validateEmbedding(vectorB);

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.length; i++) {
            dotProduct += vectorA[i] * vectorB[i];
            normA += vectorA[i] * vectorA[i];
            normB += vectorB[i] * vectorB[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }

        return dotProduct /
                (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void validateEmbedding(float[] embedding) {
        if (embedding == null || embedding.length != 768) {
            throw new IllegalArgumentException(
                    "Embedding dimension must be 768."
            );
        }
    }
}