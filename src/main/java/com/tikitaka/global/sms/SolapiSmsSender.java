package com.tikitaka.global.sms;

import com.solapi.sdk.message.exception.SolapiEmptyResponseException;
import com.solapi.sdk.message.exception.SolapiMessageNotReceivedException;
import com.solapi.sdk.message.exception.SolapiUnknownException;
import com.solapi.sdk.message.model.Message;
import com.solapi.sdk.message.service.DefaultMessageService;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.auth.exception.AuthErrorCode;
import com.tikitaka.global.exception.BusinessException;

public class SolapiSmsSender implements SmsSender {
    private final DefaultMessageService messageService;
    private final String senderNumber;

    public SolapiSmsSender(DefaultMessageService messageService, SolapiProperties properties) {
        this.messageService = messageService;
        this.senderNumber = properties.senderNumber();
    }

    @Override
    public void send(String recipientNumber, String messageText) {
        Message message = new Message();
        message.setFrom(senderNumber);
        message.setTo(recipientNumber);
        message.setText(messageText);

        try {
            messageService.send(message, null);
        } catch (SolapiMessageNotReceivedException
                 | SolapiEmptyResponseException
                 | SolapiUnknownException exception) {
            throw new BusinessException(AuthErrorCode.SMS_DELIVERY_UNAVAILABLE, exception);
        }
    }
}