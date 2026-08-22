package com.tikitaka.document.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.document.entity.Slide;

public interface SlideRepository extends JpaRepository<Slide, UUID> {

    List<Slide> findAllByDocumentIdOrderByPageNumberAsc(UUID documentId);

    Optional<Slide> findByDocumentIdAndPageNumber(
            UUID documentId,
            Integer pageNumber
    );

    void deleteAllByDocumentId(UUID documentId);
}