package com.tikitaka.note.websocket;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

@Component
public class WebSocketSessionRegistry {

    private static final CloseStatus TOKEN_EXPIRED =
            CloseStatus.POLICY_VIOLATION.withReason("ACCESS_TOKEN_EXPIRED");
    private static final CloseStatus MEMBER_REMOVED =
            CloseStatus.POLICY_VIOLATION.withReason("SPACE_MEMBER_REMOVED");

    private final TaskScheduler scheduler;
    private final ConcurrentHashMap<String, SessionState> sessions = new ConcurrentHashMap<>();

    public WebSocketSessionRegistry(
            @Qualifier("webSocketSessionTaskScheduler") TaskScheduler scheduler
    ) {
        this.scheduler = scheduler;
    }

    public void register(WebSocketSession session) {
        SessionState replaced = sessions.put(session.getId(), new SessionState(session));
        if (replaced != null) {
            close(replaced, CloseStatus.SERVER_ERROR);
        }
    }

    public void authenticate(String sessionId, UUID userId, Instant expiresAt) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalStateException("WebSocket session is not registered");
        }
        synchronized (state) {
            cancelExpiration(state);
            state.userId = userId;
            state.expirationTask = scheduler.schedule(
                    () -> closeSession(sessionId, TOKEN_EXPIRED), expiresAt);
        }
    }

    public void unregister(String sessionId) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            synchronized (state) {
                cancelExpiration(state);
            }
        }
    }

    public void closeUserSessions(UUID userId) {
        sessions.forEach((sessionId, state) -> {
            if (userId.equals(state.userId)) {
                closeSession(sessionId, MEMBER_REMOVED);
            }
        });
    }

    private void closeSession(String sessionId, CloseStatus status) {
        SessionState state = sessions.remove(sessionId);
        if (state != null) {
            close(state, status);
        }
    }

    private void close(SessionState state, CloseStatus status) {
        synchronized (state) {
            cancelExpiration(state);
            try {
                if (state.session.isOpen()) {
                    state.session.close(status);
                }
            } catch (IOException ignored) {
                // The connection is already unusable; registry cleanup is complete.
            }
        }
    }

    private void cancelExpiration(SessionState state) {
        if (state.expirationTask != null) {
            state.expirationTask.cancel(false);
            state.expirationTask = null;
        }
    }

    private static final class SessionState {
        private final WebSocketSession session;
        private volatile UUID userId;
        private ScheduledFuture<?> expirationTask;

        private SessionState(WebSocketSession session) {
            this.session = session;
        }
    }
}
