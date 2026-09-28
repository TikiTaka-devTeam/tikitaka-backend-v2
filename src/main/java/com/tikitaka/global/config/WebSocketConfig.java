package com.tikitaka.global.config;

import com.tikitaka.note.websocket.StompAccessInterceptor;
import com.tikitaka.note.websocket.ManagedWebSocketHandlerDecoratorFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final CorsProperties corsProperties;
    private final StompAccessInterceptor stompAccessInterceptor;
    private final ManagedWebSocketHandlerDecoratorFactory sessionDecoratorFactory;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.setPreserveReceiveOrder(true);
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAccessInterceptor);
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(8)
                .queueCapacity(2_000);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                .corePoolSize(4)
                .maxPoolSize(16)
                .queueCapacity(4_000);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration
                .setMessageSizeLimit(64 * 1024)
                .setSendTimeLimit(10_000)
                .setSendBufferSizeLimit(512 * 1024)
                .addDecoratorFactory(sessionDecoratorFactory);
    }
}
