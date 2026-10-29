package com.tikitaka.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.tikitaka.note.websocket.LiveStrokeStateManager;
import com.tikitaka.note.websocket.ManagedWebSocketHandlerDecoratorFactory;
import com.tikitaka.note.websocket.SharedStrokeWebSocketController;
import com.tikitaka.note.websocket.StompAccessInterceptor;
import com.tikitaka.note.websocket.WebSocketSessionRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = MonitoringStartupTests.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
        properties = {"spring.profiles.active=monitoring", "server.port=0"})
@AutoConfigureMetrics
@DirtiesContext
class MonitoringStartupTests {
    @LocalServerPort int applicationPort;

    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry properties) {
        // Boot's test support otherwise randomizes a separate management port.
        properties.add("management.server.port", () -> 9101);
    }

    @Test
    void realManagementListenerScrapesWebSocketMetricsWithoutOpeningApplicationPort() throws Exception {
        var client = HttpClient.newHttpClient();
        var scrape = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:9101/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("tikitaka_websocket_sessions", "tikitaka_websocket_executor_queued");
        var publicPort = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + applicationPort + "/actuator/prometheus")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(publicPort.statusCode()).isEqualTo(403);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = {
            "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration",
            "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration",
            "org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration"
    })
    @Import({WebSocketConfig.class, AsyncConfig.class, WebSocketSessionRegistry.class,
            ManagedWebSocketHandlerDecoratorFactory.class, LiveStrokeStateManager.class,
            SharedStrokeWebSocketController.class, WebSocketMetricsConfig.class,
            MonitoringSecurityConfig.class})
    static class Application {
        @Bean CorsProperties corsProperties() {
            return new CorsProperties(java.util.List.of("http://localhost:5173"));
        }
        @Bean StompAccessInterceptor stompAccessInterceptor() {
            return mock(StompAccessInterceptor.class);
        }
        @Bean @Order(100)
        SecurityFilterChain fallback(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(authorize -> authorize.anyRequest().denyAll()).build();
        }
    }
}
