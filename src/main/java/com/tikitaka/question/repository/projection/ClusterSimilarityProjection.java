package com.tikitaka.question.repository.projection;

import java.util.UUID;

public interface ClusterSimilarityProjection {

    UUID getClusterId();

    Double getSimilarity();
}