package com.tikitaka.note.websocket;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class SharedStrokeWebSocketPublisher {

    private final SimpMessagingTemplate messagingTemplate;
    private final LiveStrokeStateManager stateManager;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publish(SharedStrokesCommittedEvent event) {
        Set<UUID> committedClientIds = event.createdStrokes().stream()
                .map(SharedStrokesCommittedEvent.CreatedStroke::clientStrokeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        stateManager.markCommitted(event.spaceId(), event.slideId(), committedClientIds);
        messagingTemplate.convertAndSend(
                SharedStrokeWebSocketController.topic(event.spaceId(), event.slideId()),
                new Message("SHARED_STROKES_SYNCED", event.spaceId(), event.slideId(),
                        event.version(), event.createdStrokes(), event.deletedStrokeIds()));
    }

    public record Message(
            String type,
            @JsonProperty("space_id") UUID spaceId,
            @JsonProperty("slide_id") UUID slideId,
            int version,
            @JsonProperty("created_strokes") List<SharedStrokesCommittedEvent.CreatedStroke> createdStrokes,
            @JsonProperty("deleted_stroke_ids") List<UUID> deletedStrokeIds
    ) {
    }
}
