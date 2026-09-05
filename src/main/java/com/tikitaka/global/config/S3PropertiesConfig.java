package com.tikitaka.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.tikitaka.global.s3.S3Properties;

@Configuration
@EnableConfigurationProperties(S3Properties.class)
public class S3PropertiesConfig {
}
