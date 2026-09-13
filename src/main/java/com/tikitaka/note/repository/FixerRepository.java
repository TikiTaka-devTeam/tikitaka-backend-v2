package com.tikitaka.note.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.note.entity.Fixer;

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
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select f from Fixer f where f.id = :id and f.professor.id = :professorId")
    java.util.Optional<Fixer> findOwnedForUpdate(UUID id, UUID professorId);
}
