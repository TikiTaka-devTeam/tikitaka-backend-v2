package com.tikitaka.note.websocket;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class SharedStrokeWebSocketController {

    private final LiveStrokeStateManager stateManager;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/spaces/{spaceId}/slides/{slideId}/shared-strokes/live")
    public void relay(
            @DestinationVariable UUID spaceId,
            @DestinationVariable UUID slideId,
            @Payload LiveStrokeMessage message,
            SimpMessageHeaderAccessor headers
    ) {
        stateManager.accept(headers.getSessionId(), spaceId, slideId, message);
        messagingTemplate.convertAndSend(topic(spaceId, slideId),
                LiveStrokeEvent.from(spaceId, slideId, message));
    }

    static String topic(UUID spaceId, UUID slideId) {
        return "/topic/spaces/%s/slides/%s/shared-strokes".formatted(spaceId, slideId);
    }
}
