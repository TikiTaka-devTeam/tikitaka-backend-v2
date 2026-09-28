package com.tikitaka.note.websocket;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.tikitaka.space.service.SpaceMemberRemovedEvent;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpaceMemberWebSocketRevocationListenerTests {

    @Test
    void closesRemovedUsersSessionsAfterReceivingCommittedEvent() {
        WebSocketSessionRegistry sessions = mock(WebSocketSessionRegistry.class);
        SpaceMemberWebSocketRevocationListener listener =
                new SpaceMemberWebSocketRevocationListener(sessions);
        UUID userId = UUID.randomUUID();

        listener.closeRemovedMemberSessions(
                new SpaceMemberRemovedEvent(UUID.randomUUID(), userId));

        verify(sessions).closeUserSessions(userId);
    }
}
