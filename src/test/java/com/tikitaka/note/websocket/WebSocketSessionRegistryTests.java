package com.tikitaka.note.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class WebSocketSessionRegistryTests {

    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final ScheduledFuture<?> scheduledFuture = mock(ScheduledFuture.class);
    private final WebSocketSession session = mock(WebSocketSession.class);
    private final WebSocketSessionRegistry registry = new WebSocketSessionRegistry(scheduler);

    @BeforeEach
    void setup() {
        when(session.getId()).thenReturn("session");
        when(session.isOpen()).thenReturn(true);
        doReturn(scheduledFuture).when(scheduler)
                .schedule(any(Runnable.class), any(Instant.class));
    }

    @Test
    void closesSessionWhenAccessTokenExpires() throws Exception {
        Instant expiresAt = Instant.parse("2099-08-24T03:15:00Z");
        registry.register(session);
        registry.authenticate("session", UUID.randomUUID(), expiresAt);
        ArgumentCaptor<Runnable> expiration = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(expiration.capture(), org.mockito.ArgumentMatchers.eq(expiresAt));

        expiration.getValue().run();

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertThat(status.getValue().getCode()).isEqualTo(CloseStatus.POLICY_VIOLATION.getCode());
        assertThat(status.getValue().getReason()).isEqualTo("ACCESS_TOKEN_EXPIRED");
    }

    @Test
    void closesAllSessionsBelongingToRemovedUser() throws Exception {
        UUID userId = UUID.randomUUID();
        registry.register(session);
        registry.authenticate("session", userId, Instant.parse("2099-08-24T03:15:00Z"));

        registry.closeUserSessions(userId);

        ArgumentCaptor<CloseStatus> status = ArgumentCaptor.forClass(CloseStatus.class);
        verify(session).close(status.capture());
        assertThat(status.getValue().getReason()).isEqualTo("SPACE_MEMBER_REMOVED");
    }
}
