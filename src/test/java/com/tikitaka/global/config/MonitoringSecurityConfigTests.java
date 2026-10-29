package com.tikitaka.global.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class MonitoringSecurityConfigTests {
    @Test
    void scrapeIsAllowedOnlyOnManagementPortAndOtherPathsStayProtected() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.getEnvironment().getPropertySources().addFirst(
                    new MapPropertySource("test", Map.of("app.monitoring.enabled", "true")));
            context.register(TestConfig.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get("/actuator/prometheus").with(request -> {
                request.setLocalPort(9101); return request;
            })).andExpect(status().isOk());
            mvc.perform(get("/actuator/prometheus").with(request -> {
                request.setLocalPort(8080); return request;
            })).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/probe").with(request -> {
                request.setLocalPort(9101); return request;
            })).andExpect(status().isForbidden());
        }
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({MonitoringSecurityConfig.class, ProbeController.class})
    static class TestConfig {
        @Bean
        @Order(100)
        SecurityFilterChain fallback(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll()).build();
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/actuator/prometheus", "/api/v1/probe"})
        String probe() { return "probe"; }
    }
}
