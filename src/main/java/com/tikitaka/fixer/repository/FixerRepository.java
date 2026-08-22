package com.tikitaka.fixer.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.fixer.entity.Fixer;

public interface FixerRepository
        extends JpaRepository<Fixer, UUID> {

    List<Fixer> findAllBySlideIdAndProfessorIdOrderByCreatedAtAsc(
            UUID slideId,
            UUID professorId
    );

    List<Fixer> findAllBySlideIdAndProfessorIdAndCheckedFalseOrderByCreatedAtAsc(
            UUID slideId,
            UUID professorId
    );

    boolean existsByIdAndProfessorId(
            UUID id,
            UUID professorId
    );

    void deleteAllBySlideId(UUID slideId);
}