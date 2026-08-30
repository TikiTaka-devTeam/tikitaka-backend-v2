package com.tikitaka.global.sms;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.global.exception.BusinessException;

class SolapiSmsSenderTests {
    private final DefaultMessageService messageService = mock(DefaultMessageService.class);
    private final SolapiSmsSender sender = new SolapiSmsSender(
            messageService,
            new SolapiProperties("api-key", "api-secret", "0212345678"));

    @Test
    void sendsSmsWithConfiguredSenderNumber() throws Exception {
        sender.send("01012345678", "[Tikitaka] 인증번호는 123456입니다. 3분 내 입력해주세요.");

        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(messageService).send(messageCaptor.capture(), isNull());
        Message message = messageCaptor.getValue();
        assertThat(message.getFrom()).isEqualTo("0212345678");
        assertThat(message.getTo()).isEqualTo("01012345678");
        assertThat(message.getText()).isEqualTo("[Tikitaka] 인증번호는 123456입니다. 3분 내 입력해주세요.");
    }

    @Test
    void convertsSolapiFailureToSafeBusinessError() throws Exception {
        when(messageService.send(any(Message.class), isNull()))
                .thenThrow(new SolapiUnknownException("external error containing provider details"));

        assertThatThrownBy(() -> sender.send("01012345678", "message"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> assertThat(((BusinessException) exception).getErrorCode())
                        .isEqualTo(AuthErrorCode.SMS_DELIVERY_UNAVAILABLE))
                .hasMessage("인증번호 문자를 발송할 수 없습니다.");
    }
}
