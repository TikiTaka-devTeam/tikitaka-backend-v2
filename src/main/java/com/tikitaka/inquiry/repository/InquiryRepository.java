package com.tikitaka.inquiry.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.tikitaka.inquiry.entity.Inquiry;

public interface InquiryRepository extends JpaRepository<Inquiry, UUID> {

    List<Inquiry> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}