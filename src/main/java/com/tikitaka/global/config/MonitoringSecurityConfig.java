package com.tikitaka.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(name = "app.monitoring.enabled", havingValue = "true")
public class MonitoringSecurityConfig {
    @Bean
    @Order(0)
    SecurityFilterChain monitoringSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher(request -> request.getLocalPort() == 9101)
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus", "/actuator/health").permitAll()
                        .anyRequest().denyAll())
                .build();
    }
}
