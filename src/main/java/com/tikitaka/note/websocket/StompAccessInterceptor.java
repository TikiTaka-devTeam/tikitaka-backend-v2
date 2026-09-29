package com.tikitaka.note.websocket;

import com.tikitaka.global.security.AuthenticatedUser;
import com.tikitaka.global.security.JwtProvider;
import com.tikitaka.global.security.ValidatedAccessToken;
import com.tikitaka.user.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StompAccessInterceptor implements ChannelInterceptor {

    private static final Pattern SEND_DESTINATION = Pattern.compile(
            "^/app/spaces/([0-9a-fA-F-]{36})/slides/([0-9a-fA-F-]{36})/shared-strokes/live$");
    private static final Pattern SUBSCRIBE_DESTINATION = Pattern.compile(
            "^/topic/spaces/([0-9a-fA-F-]{36})/slides/([0-9a-fA-F-]{36})/shared-strokes$");

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final StrokeWebSocketAccessService accessService;
    private final WebSocketSessionRegistry sessionRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class);
        if (accessor == null) {
            throw new MessageDeliveryException("WS_INVALID_FRAME");
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            authenticate(accessor);
        } else if (command == StompCommand.SEND) {
            UUID userId = userId(accessor.getUser());
            Destination destination = destination(accessor, SEND_DESTINATION);
            accessService.requireSharedEdit(userId, destination.spaceId(), destination.slideId());
        } else if (command == StompCommand.SUBSCRIBE) {
            UUID userId = userId(accessor.getUser());
            Destination destination = destination(accessor, SUBSCRIBE_DESTINATION);
            accessService.requireSubscription(userId, destination.spaceId(), destination.slideId());
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            unauthorized();
        }
        try {
            ValidatedAccessToken token = jwtProvider.validateAccessTokenDetails(
                    authorization.substring(7));
            if (!userRepository.existsById(token.userId())) {
                unauthorized();
            }
            String sessionId = accessor.getSessionId();
            if (sessionId == null) {
                unauthorized();
            }
            sessionRegistry.authenticate(sessionId, token.userId(), token.expiresAt());
            AuthenticatedUser principal = new AuthenticatedUser(token.userId());
            accessor.setUser(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        } catch (JwtException | IllegalArgumentException | IllegalStateException exception) {
            unauthorized();
        }
    }

    private UUID userId(Principal principal) {
        if (!(principal instanceof Authentication authentication)
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            return unauthorized();
        }
        return user.userId();
    }

    private Destination destination(StompHeaderAccessor accessor, Pattern pattern) {
        String value = accessor.getDestination();
        Matcher matcher = value == null ? null : pattern.matcher(value);
        if (matcher == null || !matcher.matches()) {
            throw new MessageDeliveryException("WS_INVALID_DESTINATION");
        }
        try {
            return new Destination(UUID.fromString(matcher.group(1)), UUID.fromString(matcher.group(2)));
        } catch (IllegalArgumentException exception) {
            throw new MessageDeliveryException("WS_INVALID_DESTINATION");
        }
    }

    private <T> T unauthorized() {
        throw new MessageDeliveryException("WS_UNAUTHORIZED");
    }

    private record Destination(UUID spaceId, UUID slideId) {
    }
}
