package com.tikitaka.space.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.Schedule;

public interface ScheduleRepository
        extends JpaRepository<Schedule, UUID> {

    List<Schedule> findAllBySpaceId(
            UUID spaceId
    );

    void deleteAllBySpaceId(
            UUID spaceId
    );

    /**
     * 특정 교수가 가지고 있는
     * 활성 Space들의 모든 수업 시간 조회
     */
    List<Schedule> findAllBySpaceProfessorIdAndSpaceActiveStatusTrue(
            UUID professorId
    );
}