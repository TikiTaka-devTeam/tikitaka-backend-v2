package com.tikitaka.inquiry.dto;

import java.time.Instant;
import java.util.UUID;
import com.tikitaka.inquiry.entity.Inquiry;
import com.tikitaka.inquiry.entity.InquiryType;

public record InquiryCreateResponse(
        UUID inquiryId,
        InquiryType type,
        String status,
        Instant createdAt
) {
    public static InquiryCreateResponse from(Inquiry inquiry) {
        return new InquiryCreateResponse(
                inquiry.getId(), inquiry.getType(), inquiry.getStatus(), inquiry.getCreatedAt());
    }
}
