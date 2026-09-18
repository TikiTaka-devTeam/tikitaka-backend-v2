package com.tikitaka.question.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.question.entity.QuestionCluster;

public interface QuestionClusterRepository
        extends JpaRepository<QuestionCluster, UUID> {
}