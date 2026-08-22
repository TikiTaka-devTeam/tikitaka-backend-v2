package com.tikitaka.document.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.document.entity.Document;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllBySpaceIdOrderByCreatedAtDesc(UUID spaceId);
}