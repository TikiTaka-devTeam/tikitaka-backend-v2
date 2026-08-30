package com.tikitaka.global.sms;

public interface SmsSender {
    void send(String recipientNumber, String messageText);
}