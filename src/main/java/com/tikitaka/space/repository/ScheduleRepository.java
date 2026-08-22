package com.tikitaka.space.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.Schedule;

public interface ScheduleRepository extends JpaRepository<Schedule, UUID> {

    List<Schedule> findAllBySpaceId(UUID spaceId);

    void deleteAllBySpaceId(UUID spaceId);
}