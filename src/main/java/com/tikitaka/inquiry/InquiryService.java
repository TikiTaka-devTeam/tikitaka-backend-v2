package com.tikitaka.inquiry;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.tikitaka.global.exception.BusinessException;
import com.tikitaka.inquiry.dto.InquiryCreateRequest;
import com.tikitaka.inquiry.dto.InquiryCreateResponse;
import com.tikitaka.inquiry.entity.Inquiry;
import com.tikitaka.inquiry.repository.InquiryRepository;
import com.tikitaka.user.UserErrorCode;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;

@Service
@Transactional(readOnly = true)
public class InquiryService {
    private final InquiryRepository inquiryRepository;
    private final UserRepository userRepository;

    public InquiryService(InquiryRepository inquiryRepository, UserRepository userRepository) {
        this.inquiryRepository = inquiryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public InquiryCreateResponse create(UUID userId, InquiryCreateRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        Inquiry inquiry = Inquiry.create(
                user, request.type(), request.title().trim(), request.content().trim());
        return InquiryCreateResponse.from(inquiryRepository.save(inquiry));
    }
}
