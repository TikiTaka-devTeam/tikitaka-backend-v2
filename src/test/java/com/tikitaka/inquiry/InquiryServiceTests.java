package com.tikitaka.inquiry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.tikitaka.inquiry.dto.InquiryCreateRequest;
import com.tikitaka.inquiry.dto.InquiryCreateResponse;
import com.tikitaka.inquiry.entity.Inquiry;
import com.tikitaka.inquiry.entity.InquiryType;
import com.tikitaka.inquiry.repository.InquiryRepository;
import com.tikitaka.user.entity.AccountType;
import com.tikitaka.user.entity.User;
import com.tikitaka.user.repository.UserRepository;

class InquiryServiceTests {
    private final InquiryRepository inquiryRepository = mock(InquiryRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final InquiryService inquiryService = new InquiryService(inquiryRepository, userRepository);

    @Test
    void createsSubmittedInquiryForAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        User user = User.createLocal(
                "student@example.com", "encoded", "김선민", AccountType.STUDENT,
                "01012345678", "단국대학교", "컴퓨터공학과", "20231370", null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(inquiryRepository.save(any(Inquiry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        InquiryCreateResponse response = inquiryService.create(userId, new InquiryCreateRequest(
                InquiryType.ERROR_REPORT, "  강의자료 오류 문의  ", "  PDF가 열리지 않습니다.  "));

        assertThat(response.type()).isEqualTo(InquiryType.ERROR_REPORT);
        assertThat(response.status()).isEqualTo("SUBMITTED");
    }
}
