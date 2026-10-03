package com.tikitaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import com.tikitaka.global.security.AuthenticatedUser;
import com.tikitaka.global.security.JwtAuthenticationFilter;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.global.security.RestAccessDeniedHandler;
import com.tikitaka.global.security.RestAuthenticationEntryPoint;
import com.tikitaka.global.security.SecurityErrorWriter;

import jakarta.servlet.http.HttpServletResponse;

class SwaggerSecurityConfigTests {
    private static final UUID USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @ParameterizedTest
    @ValueSource(strings = {"/swagger-ui/index.html", "/swagger-ui.html", "/swagger-ui/swagger-ui.css",
            "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"})
    void productionRequiresBrowserBasicLoginForEverySwaggerResource(String path) throws Exception {
        try (var context = context("prod", credentials())) {
            MockMvc mvc = mvc(context);
            mvc.perform(get(path)).andExpect(status().isUnauthorized())
                    .andExpect(result -> assertThat(result.getResponse().getHeader("WWW-Authenticate"))
                            .startsWith("Basic realm=\"TikiTaka Swagger\""));
            mvc.perform(get(path).with(httpBasic("team", "test-password")))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void rejectsWrongPasswordAndJwtForSwagger() throws Exception {
        try (var context = context("prod", credentials())) {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/v3/api-docs").with(httpBasic("team", "wrong")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/v3/api-docs").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    void apiKeepsJwtAuthenticationAndRejectsSwaggerCredentials() throws Exception {
        try (var context = context("prod", credentials())) {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/api/v1/probe").with(httpBasic("team", "test-password")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist("WWW-Authenticate"));
            mvc.perform(get("/api/v1/probe").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk()).andExpect(content().string(USER_ID.toString()));
            mvc.perform(get("/api/v1/probe")).andExpect(status().isUnauthorized());
            assertThat(context.getBean("jwtFilterRegistration", FilterRegistrationBean.class).isEnabled()).isFalse();
        }
    }

    @Test
    void missingCredentialsLockSwaggerWithoutPreventingApiStartup() throws Exception {
        try (var context = context("prod", Map.of())) {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/v3/api-docs").with(httpBasic("team", "test-password")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/v1/probe").header("Authorization", "Bearer valid-token"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void localSwaggerRemainsPublicAndApiRemainsProtected() throws Exception {
        try (var context = context("local", Map.of())) {
            MockMvc mvc = mvc(context);
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
            mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
            mvc.perform(get("/api/v1/probe")).andExpect(status().isUnauthorized());
        }
    }

    private Map<String, Object> credentials() {
        return Map.of("swagger.auth.username", "team", "swagger.auth.password", "test-password");
    }

    private AnnotationConfigWebApplicationContext context(String profile, Map<String, Object> properties) {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().setActiveProfiles(profile);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(TestConfig.class);
        context.refresh();
        return context;
    }

    private MockMvc mvc(AnnotationConfigWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, SwaggerSecurityConfig.class, ProbeController.class})
    static class TestConfig {
        @Bean
        JwtAuthenticationFilter jwtAuthenticationFilter() throws Exception {
            JwtProvider provider = mock(JwtProvider.class);
            when(provider.validateAccessToken("valid-token")).thenReturn(USER_ID);
            SecurityErrorWriter writer = mock(SecurityErrorWriter.class);
            doAnswer(invocation -> {
                invocation.<HttpServletResponse>getArgument(0).setStatus(401);
                return null;
            }).when(writer).write(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            return new JwtAuthenticationFilter(provider, writer);
        }

        @Bean
        RestAuthenticationEntryPoint authenticationEntryPoint() throws Exception {
            RestAuthenticationEntryPoint entryPoint = mock(RestAuthenticationEntryPoint.class);
            doAnswer(invocation -> {
                invocation.<HttpServletResponse>getArgument(1).setStatus(401);
                return null;
            }).when(entryPoint).commence(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            return entryPoint;
        }

        @Bean
        RestAccessDeniedHandler accessDeniedHandler() {
            return mock(RestAccessDeniedHandler.class);
        }
    }

    @RestController
    static class ProbeController {
        @GetMapping({"/swagger-ui/index.html", "/swagger-ui.html", "/swagger-ui/swagger-ui.css",
                "/v3/api-docs", "/v3/api-docs/swagger-config", "/v3/api-docs.yaml"})
        String swagger() {
            return "swagger";
        }

        @GetMapping("/api/v1/probe")
        String api(Authentication authentication) {
            return ((AuthenticatedUser) authentication.getPrincipal()).userId().toString();
        }
    }
}
