package com.tikitaka.note.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tikitaka.global.security.AuthenticatedUser;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.user.repository.UserRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

class StompAccessInterceptorTests {

    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final UserRepository users = mock(UserRepository.class);
    private final StrokeWebSocketAccessService access = mock(StrokeWebSocketAccessService.class);
    private final StompAccessInterceptor interceptor =
            new StompAccessInterceptor(jwtProvider, users, access);
    private final MessageChannel channel = mock(MessageChannel.class);

    @Test
    void connectAuthenticatesBearerTokenAndStoresPrincipal() {
        UUID userId = UUID.randomUUID();
        when(jwtProvider.validateAccessToken("token")).thenReturn(userId);
        when(users.existsById(userId)).thenReturn(true);
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.CONNECT);
        headers.setNativeHeader("Authorization", "Bearer token");
        Message<byte[]> message = message(headers);

        interceptor.preSend(message, channel);

        assertThat(headers.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);
        assertThat(((AuthenticatedUser) ((UsernamePasswordAuthenticationToken) headers.getUser())
                .getPrincipal()).userId()).isEqualTo(userId);
    }

    @Test
    void subscribeRechecksSpaceAndSlideMembership() {
        UUID userId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        UUID slideId = UUID.randomUUID();
        StompHeaderAccessor headers = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        headers.setDestination("/topic/spaces/%s/slides/%s/shared-strokes"
                .formatted(spaceId, slideId));
        headers.setUser(new UsernamePasswordAuthenticationToken(
                new AuthenticatedUser(userId), null, List.of()));

        interceptor.preSend(message(headers), channel);

        verify(access).requireSubscription(userId, spaceId, slideId);
    }

    private Message<byte[]> message(StompHeaderAccessor headers) {
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }
}
