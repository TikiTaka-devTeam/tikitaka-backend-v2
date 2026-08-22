package com.tikitaka.space.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.space.entity.Space;

public interface SpaceRepository extends JpaRepository<Space, UUID> {

    Optional<Space> findBySpaceCode(String spaceCode);

    boolean existsBySpaceCode(String spaceCode);
}