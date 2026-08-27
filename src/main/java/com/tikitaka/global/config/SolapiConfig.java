package com.tikitaka.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.solapi.sdk.SolapiClient;
import com.solapi.sdk.message.service.DefaultMessageService;
import com.tikitaka.global.sms.SmsSender;
import com.tikitaka.global.sms.SolapiProperties;
import com.tikitaka.global.sms.SolapiSmsSender;

@Configuration
@Profile("!test")
@EnableConfigurationProperties(SolapiProperties.class)
public class SolapiConfig {

    @Bean
    DefaultMessageService solapiMessageService(SolapiProperties properties) {
        return SolapiClient.INSTANCE.createInstance(properties.apiKey(), properties.apiSecret());
    }

    @Bean
    SmsSender smsSender(DefaultMessageService messageService, SolapiProperties properties) {
        return new SolapiSmsSender(messageService, properties);
    }
}