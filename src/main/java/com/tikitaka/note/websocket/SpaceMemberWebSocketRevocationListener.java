package com.tikitaka.note.websocket;

import com.tikitaka.space.service.SpaceMemberRemovedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SpaceMemberWebSocketRevocationListener {

    private final WebSocketSessionRegistry sessionRegistry;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void closeRemovedMemberSessions(SpaceMemberRemovedEvent event) {
        sessionRegistry.closeUserSessions(event.userId());
    }
}
