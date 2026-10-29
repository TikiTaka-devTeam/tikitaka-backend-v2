package com.tikitaka.global.config;

import com.tikitaka.note.websocket.WebSocketSessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class WebSocketMetricsConfig {
    @Bean
    MeterBinder webSocketMetrics(@Lazy WebSocketSessionRegistry sessions,
            @Lazy @Qualifier("clientInboundChannelExecutor") ThreadPoolTaskExecutor inbound,
            @Lazy @Qualifier("clientOutboundChannelExecutor") ThreadPoolTaskExecutor outbound) {
        return registry -> {
            Gauge.builder("tikitaka.websocket.sessions", sessions, WebSocketSessionRegistry::size)
                    .description("Registered transport sessions, including unauthenticated connections")
                    .register(registry);
            for (var entry : java.util.Map.of("inbound", inbound, "outbound", outbound).entrySet()) {
                Gauge.builder("tikitaka.websocket.executor.queued", entry.getValue(),
                                executor -> executor.getThreadPoolExecutor().getQueue().size())
                        .tag("direction", entry.getKey()).register(registry);
                Gauge.builder("tikitaka.websocket.executor.active", entry.getValue(),
                                ThreadPoolTaskExecutor::getActiveCount)
                        .tag("direction", entry.getKey()).register(registry);
            }
        };
    }
}
